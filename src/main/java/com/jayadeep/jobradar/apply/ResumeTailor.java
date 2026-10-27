package com.jayadeep.jobradar.apply;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayadeep.jobradar.apply.CandidateProfile.Experience;
import com.jayadeep.jobradar.apply.CandidateProfile.Project;
import com.jayadeep.jobradar.apply.CandidateProfile.Resume;
import com.jayadeep.jobradar.apply.CandidateProfile.SkillGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tailors the master resume to one job description. Claude may only choose, reorder and lightly reword
 * what is already in the master resume; employers, titles, dates, education and certifications are always
 * copied from the master, and {@link FactGuard} throws out anything with an unknown metric or skill.
 */
@Component
public class ResumeTailor {

    private static final Logger log = LoggerFactory.getLogger(ResumeTailor.class);
    static final int MAX_BULLETS_CURRENT = 6;
    static final int MAX_BULLETS_OTHER = 5;

    private final ClaudeClient claude;
    private final ObjectMapper mapper;

    public ResumeTailor(ClaudeClient claude, ObjectMapper mapper) {
        this.claude = claude;
        this.mapper = mapper;
    }

    public Tailored tailor(Resume master, String jobTitle, String company, String jobDescription) {
        if (!claude.enabled()) {
            return new Tailored(trimmed(master), List.of("No Claude API key: used your master resume as-is."));
        }
        try {
            JsonNode reply = claude.json(systemPrompt(), userPrompt(master, jobTitle, company, jobDescription), 4000);
            return merge(master, reply);
        } catch (RuntimeException e) {
            log.warn("Resume tailoring failed, using master resume: {}", e.getMessage());
            return new Tailored(trimmed(master), List.of("Tailoring failed (" + e.getMessage() + "): used your master resume."));
        }
    }

    /** Merges Claude's suggestions into the master resume, keeping only what passes the fact guard. */
    Tailored merge(Resume master, JsonNode reply) {
        FactGuard guard = new FactGuard(master);
        List<String> notes = new ArrayList<>();

        String summary = reply.path("summary").asText("").strip();
        if (summary.isEmpty() || !guard.numbersAreKnown(summary)) {
            if (!summary.isEmpty()) {
                notes.add("Rejected tailored summary (it contained a number not in your resume).");
            }
            summary = master.summary();
        }

        List<SkillGroup> skills = new ArrayList<>();
        for (JsonNode g : reply.path("skills")) {
            String items = guard.filterSkills(g.path("items").asText(""));
            String category = g.path("category").asText("").strip();
            if (!items.isEmpty() && !category.isEmpty()) {
                skills.add(new SkillGroup(category, items));
            }
        }
        if (skills.isEmpty()) {
            skills = master.skills();
        }

        Map<String, List<String>> bulletsByCompany = new LinkedHashMap<>();
        for (JsonNode e : reply.path("experience")) {
            List<String> bullets = new ArrayList<>();
            e.path("bullets").forEach(b -> bullets.add(b.asText("").strip()));
            bulletsByCompany.put(key(e.path("company").asText("")), bullets);
        }

        List<Experience> experience = new ArrayList<>();
        for (int i = 0; i < master.experience().size(); i++) {
            Experience job = master.experience().get(i);
            int limit = i == 0 ? MAX_BULLETS_CURRENT : MAX_BULLETS_OTHER;
            List<String> proposed = bulletsByCompany.getOrDefault(key(job.company()), List.of());
            List<String> kept = new ArrayList<>();
            for (String b : proposed) {
                if (b.isEmpty()) {
                    continue;
                }
                if (guard.numbersAreKnown(b)) {
                    kept.add(b);
                } else {
                    notes.add("Dropped a " + job.company() + " bullet with a number not in your resume: \"" + b + "\"");
                }
            }
            if (kept.isEmpty()) {
                kept = job.bullets();
            }
            experience.add(new Experience(job.company(), job.location(), job.title(), job.dates(),
                    kept.subList(0, Math.min(limit, kept.size()))));
        }

        Set<String> keepProjects = new HashSet<>();
        reply.path("projects").forEach(p -> keepProjects.add(key(p.asText(""))));
        List<Project> projects = master.projects().stream()
                .filter(p -> keepProjects.contains(key(p.name())))
                .toList();

        Resume tailored = new Resume(summary, skills, experience, projects, master.education(), master.certifications());
        return new Tailored(tailored, notes);
    }

    /** The master resume with bullet counts capped, used when tailoring isn't available. */
    static Resume trimmed(Resume master) {
        List<Experience> experience = new ArrayList<>();
        for (int i = 0; i < master.experience().size(); i++) {
            Experience job = master.experience().get(i);
            int limit = i == 0 ? MAX_BULLETS_CURRENT : MAX_BULLETS_OTHER;
            experience.add(new Experience(job.company(), job.location(), job.title(), job.dates(),
                    job.bullets().subList(0, Math.min(limit, job.bullets().size()))));
        }
        return new Resume(master.summary(), master.skills(), experience, List.of(), master.education(), master.certifications());
    }

    private static String key(String s) {
        return s == null ? "" : s.strip().toLowerCase(Locale.ROOT);
    }

    private static String systemPrompt() {
        return """
                You tailor a software engineer's resume to one job posting. Hard rules:
                - Use ONLY facts in the master resume. Never invent employers, projects, skills, tools,
                  numbers or outcomes. Every number you write must appear in the master resume.
                - You may choose which bullets to keep, reorder them so the most relevant come first,
                  and lightly reword them to use the posting's terminology where it is truthful.
                - Keep every employer from the master resume, with the exact company name.
                - Skills: keep only skills from the master resume; put the posting's most relevant first;
                  drop skills irrelevant to the posting. Keep at most 7 categories.
                - Summary: 2-3 sentences, starts with "Software Engineer", no first person.
                - Projects: list the names of master-resume projects worth including (may be empty).
                Reply with ONLY this JSON, no code fences:
                {"summary": "...",
                 "skills": [{"category": "...", "items": "a, b, c"}],
                 "experience": [{"company": "...", "bullets": ["...", "..."]}],
                 "projects": ["exact project name"]}
                At most 6 bullets for the first employer and 5 for each other employer.
                """;
    }

    private String userPrompt(Resume master, String title, String company, String description) {
        String jd = description == null ? "" : description;
        if (jd.length() > 15_000) {
            jd = jd.substring(0, 15_000);
        }
        try {
            return "Master resume (JSON):\n" + mapper.writeValueAsString(master)
                    + "\n\nJob: " + title + " at " + company + "\n\nJob description:\n" + jd;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A tailored resume plus notes on anything the fact guard rejected. */
    public record Tailored(Resume resume, List<String> notes) {
    }
}
