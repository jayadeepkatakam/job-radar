package com.jayadeep.jobradar.apply;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Decides an answer for each form field. Facts come only from candidate.yml; Claude is used to draft
 * open-ended answers ("Why do you want to work here?") and to pick options for questions the rules
 * don't cover. Legal, work-authorization and self-identification questions are never sent to Claude:
 * they are answered from candidate.yml or left blank for you.
 */
@Component
public class FieldAnswerer {

    private static final Logger log = LoggerFactory.getLogger(FieldAnswerer.class);

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private static final Pattern FIRST = p("\\b(first|given|preferred|legal first) name");
    private static final Pattern LAST = p("\\b(last|family|sur) ?name");
    private static final Pattern FULL = p("^(full |your |legal )?name$|\\bfull name");
    private static final Pattern EMAIL = p("e-?mail");
    private static final Pattern PHONE = p("phone|mobile|cell");
    private static final Pattern LINKEDIN = p("linked ?in");
    private static final Pattern GITHUB = p("github");
    private static final Pattern WEBSITE = p("website|portfolio|personal (site|url)|other url");
    private static final Pattern LOCATION = p("^(current )?location|where are you (currently )?(located|based)|city.*state|^city$|current city|address");
    private static final Pattern COMPANY = p("current (company|employer)|^company$|^organization$|most recent (company|employer)");
    private static final Pattern TITLE = p("current (job )?title|current role|^title$|most recent title");
    private static final Pattern WITHOUT_SPONSORSHIP = p("without (the need for |requiring )?(any )?(current or future )?(visa )?sponsorship");
    private static final Pattern SPONSORSHIP = p("sponsor|h-?1b|visa status|immigration");
    private static final Pattern AUTHORIZED = p("authori[sz]ed to work|eligible to work|legally (able|allowed|permitted|authori)|right to work|work authori[sz]ation");
    private static final Pattern RELOCATE = p("relocat");
    private static final Pattern SALARY = p("salary|compensation|pay (range|expectation)|desired pay");
    private static final Pattern NOTICE = p("notice period|start date|when can you start|available to start|earliest start");
    private static final Pattern HEARD = p("how did you (hear|find|learn)|where did you (hear|find)|^source$|referral source");
    private static final Pattern YEARS = p("years of (professional |relevant |software |industry )?experience|how many years");
    private static final Pattern GENDER = p("gender|^sex$");
    private static final Pattern RACE = p("race|ethnic|hispanic|latino");
    private static final Pattern VETERAN = p("veteran");
    private static final Pattern DISABILITY = p("disabilit");
    private static final Pattern PRONOUNS = p("pronoun");
    private static final Pattern RESUME = p("resume|r\\u00e9sum\\u00e9|\\bcv\\b");
    private static final Pattern COVER = p("cover letter");
    /** Questions only you can answer, never auto-filled. */
    private static final Pattern PERSONAL = p("certify|attest|acknowledge|i agree|consent|privacy|terms (and|&) conditions|signature|"
            + "criminal|convicted|felony|background check|drug (test|screen)|clearance|over (the age of )?18|\\bage\\b|"
            + "non-?compete|previously (worked|been employed)|related to (anyone|an? employee)|relatives?\\b|referred by|employee referral");

    private final ClaudeClient claude;
    private final ObjectMapper mapper;

    public FieldAnswerer(ClaudeClient claude, ObjectMapper mapper) {
        this.claude = claude;
        this.mapper = mapper;
    }

    /**
     * @param resumeFile path of the tailored resume PDF, uploaded to resume/CV file inputs
     * @return one answer per field id, in field order
     */
    public Map<String, Answer> answer(List<FormField> fields, CandidateProfile c, String company, String jobTitle,
                                      String jobDescription, String resumeFile, String resumeText) {
        Map<String, Answer> answers = new LinkedHashMap<>();
        List<FormField> forClaude = new ArrayList<>();

        for (FormField f : fields) {
            Answer a = byRule(f, c, resumeFile);
            if (a != null) {
                answers.put(f.id(), a);
            } else {
                answers.put(f.id(), Answer.blank("No answer in candidate.yml"));
                if (!f.kind().startsWith("checkbox")) {
                    forClaude.add(f);
                }
            }
        }

        if (!forClaude.isEmpty() && claude.enabled()) {
            try {
                answers.putAll(askClaude(forClaude, c, company, jobTitle, jobDescription, resumeText));
            } catch (RuntimeException e) {
                log.warn("Claude couldn't draft answers: {}", e.getMessage());
            }
        }
        return answers;
    }

    /** Rule-based answer, a deliberate blank, or null when the rules don't recognize the question. */
    Answer byRule(FormField f, CandidateProfile c, String resumeFile) {
        String q = f.label();

        if ("file".equals(f.kind())) {
            if (COVER.matcher(q).find()) {
                return Answer.blank("Cover letter: attach one yourself if you want to");
            }
            return RESUME.matcher(q).find() || q.isBlank() ? new Answer(resumeFile, "file", false, "Tailored resume") : Answer.blank("Unknown file upload");
        }
        if (!f.value().isBlank() && !"checkbox".equals(f.kind())) {
            return new Answer(null, "none", false, "Already filled (kept as is)");
        }

        // Self-identification: your setting, or the "decline" option.
        if (GENDER.matcher(q).find()) {
            return eeo(f, c.eeo().gender());
        }
        if (RACE.matcher(q).find()) {
            return eeo(f, c.eeo().race());
        }
        if (VETERAN.matcher(q).find()) {
            return eeo(f, c.eeo().veteran());
        }
        if (DISABILITY.matcher(q).find()) {
            return eeo(f, c.eeo().disability());
        }
        if (PRONOUNS.matcher(q).find()) {
            return Answer.blank("Pronouns: optional, left for you");
        }

        // Work authorization: only ever from candidate.yml.
        if (WITHOUT_SPONSORSHIP.matcher(q).find()) {
            Boolean needs = c.requiresSponsorship();
            if (Boolean.TRUE.equals(needs)) {
                return yesNo(f, false);
            }
            if (Boolean.FALSE.equals(needs) && Boolean.TRUE.equals(c.authorizedToWork())) {
                return yesNo(f, true);
            }
            return Answer.blank("Set authorized-to-work and requires-sponsorship in candidate.yml");
        }
        if (SPONSORSHIP.matcher(q).find()) {
            return c.requiresSponsorship() == null
                    ? Answer.blank("Set requires-sponsorship in candidate.yml")
                    : yesNo(f, c.requiresSponsorship());
        }
        if (AUTHORIZED.matcher(q).find()) {
            return c.authorizedToWork() == null
                    ? Answer.blank("Set authorized-to-work in candidate.yml")
                    : yesNo(f, c.authorizedToWork());
        }
        if (PERSONAL.matcher(q).find()) {
            return Answer.blank("Legal or personal question: answer it yourself");
        }

        if ("checkbox".equals(f.kind()) || "checkboxGroup".equals(f.kind())) {
            return null;
        }

        if (FIRST.matcher(q).find()) {
            return text(c.firstName());
        }
        if (LAST.matcher(q).find()) {
            return text(c.lastName());
        }
        if (FULL.matcher(q).find()) {
            return text(c.fullName());
        }
        if (EMAIL.matcher(q).find() || "email".equals(f.kind())) {
            return text(c.email());
        }
        if (PHONE.matcher(q).find() || "tel".equals(f.kind())) {
            return text(c.phone());
        }
        if (LINKEDIN.matcher(q).find()) {
            return text(c.linkedin());
        }
        if (GITHUB.matcher(q).find()) {
            return text(c.github());
        }
        if (WEBSITE.matcher(q).find()) {
            return text(c.website() != null && !c.website().isBlank() ? c.website() : c.github());
        }
        if (LOCATION.matcher(q).find()) {
            return text(c.location().isBlank() ? null : c.location());
        }
        if (COMPANY.matcher(q).find()) {
            return text(c.currentCompany());
        }
        if (TITLE.matcher(q).find()) {
            return text(c.currentTitle());
        }
        if (RELOCATE.matcher(q).find()) {
            return c.willingToRelocate() == null ? Answer.blank("Set willing-to-relocate in candidate.yml") : yesNo(f, c.willingToRelocate());
        }
        if (SALARY.matcher(q).find()) {
            return c.salaryExpectation() == null || c.salaryExpectation().isBlank()
                    ? Answer.blank("Salary: set salary-expectation in candidate.yml or answer yourself")
                    : choiceOrText(f, c.salaryExpectation());
        }
        if (NOTICE.matcher(q).find() && !"date".equals(f.kind())) {
            return text(c.noticePeriod());
        }
        if (HEARD.matcher(q).find()) {
            Answer a = choiceOrText(f, c.howDidYouHear());
            if (a.isBlank() && f.isChoice()) {
                a = choiceOrText(f, "Other");
            }
            return a;
        }
        if (YEARS.matcher(q).find() && !q.toLowerCase().matches(".*\\b(java|python|go|kafka|aws|azure|sql|spring|react)\\b.*")) {
            return c.yearsExperience() == null ? null : choiceOrText(f, String.valueOf(c.yearsExperience()));
        }
        return null;
    }

    private static Answer text(String value) {
        return value == null || value.isBlank() ? Answer.blank("Not set in candidate.yml") : Answer.fromProfile(value);
    }

    private static Answer yesNo(FormField f, boolean yes) {
        return choiceOrText(f, yes ? "Yes" : "No");
    }

    /** For choice fields, maps the intended answer to an actual option label. */
    private static Answer choiceOrText(FormField f, String wanted) {
        if (wanted == null || wanted.isBlank()) {
            return Answer.blank("Not set in candidate.yml");
        }
        if (!f.isChoice() || f.options().isEmpty()) {
            return Answer.fromProfile(wanted);
        }
        Optional<FormField.Option> o = OptionMatcher.match(f.options(), wanted);
        return o.map(opt -> Answer.fromProfile(opt.label()))
                .orElseGet(() -> Answer.blank("No option matched \"" + wanted + "\""));
    }

    private static Answer eeo(FormField f, String setting) {
        String wanted = setting == null || setting.isBlank() ? "Decline to self-identify" : setting;
        if (!f.isChoice()) {
            return setting == null || setting.isBlank() ? Answer.blank("Self-identification: left blank") : Answer.fromProfile(setting);
        }
        Answer a = choiceOrText(f, wanted);
        return a.isBlank() ? Answer.blank("Self-identification: no matching option, left for you") : a;
    }

    private Map<String, Answer> askClaude(List<FormField> fields, CandidateProfile c, String company, String jobTitle,
                                          String jobDescription, String resumeText) {
        List<Map<String, Object>> qs = new ArrayList<>();
        for (FormField f : fields) {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("id", f.id());
            q.put("question", f.label());
            q.put("type", f.kind());
            if (!f.options().isEmpty()) {
                q.put("options", f.options().stream().map(FormField.Option::label).toList());
            }
            if (f.maxLength() != null) {
                q.put("maxLength", f.maxLength());
            }
            qs.add(q);
        }
        String system = """
                You fill in a job application for the candidate below. Answer ONLY from the candidate's resume
                and profile. Rules:
                - If a question asks for a fact you cannot find in the resume or profile, answer null.
                  Never guess personal facts (dates, addresses, references, legal status, clearances).
                - For questions with options, the answer must be copied exactly from the options, or null.
                - For yes/no questions about skills or experience, answer "Yes" only if the resume clearly shows it.
                - For open-ended questions (why this company, describe a project, etc.), write a specific,
                  truthful answer in first person, 60-150 words, grounded in the resume and the job description.
                  Respect maxLength. No invented numbers.
                Reply with ONLY JSON: {"answers": [{"id": "...", "value": "..." or null, "confident": true/false}]}
                """;
        String jd = jobDescription == null ? "" : jobDescription;
        if (jd.length() > 12_000) {
            jd = jd.substring(0, 12_000);
        }
        String user;
        try {
            user = "Candidate: " + c.fullName() + ", " + c.currentTitle() + " at " + c.currentCompany()
                    + ", " + c.yearsExperience() + " years of experience.\n\nResume:\n" + resumeText
                    + "\n\nJob: " + jobTitle + " at " + company + "\n\nJob description:\n" + jd
                    + "\n\nQuestions (JSON):\n" + mapper.writeValueAsString(qs);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        JsonNode reply = claude.json(system, user, 3000);
        Map<String, FormField> byId = new LinkedHashMap<>();
        fields.forEach(f -> byId.put(f.id(), f));
        Map<String, Answer> out = new LinkedHashMap<>();
        for (JsonNode a : reply.path("answers")) {
            FormField f = byId.get(a.path("id").asText());
            if (f == null || a.path("value").isNull() || a.path("value").asText("").isBlank()) {
                continue;
            }
            String value = a.path("value").asText().strip();
            if (f.isChoice() && !f.options().isEmpty()) {
                Optional<FormField.Option> o = OptionMatcher.match(f.options(), value);
                if (o.isEmpty()) {
                    continue;
                }
                value = o.get().label();
            }
            if (f.maxLength() != null && value.length() > f.maxLength()) {
                value = value.substring(0, f.maxLength());
            }
            boolean openEnded = "textarea".equals(f.kind()) || value.length() > 80;
            boolean confident = a.path("confident").asBoolean(false);
            out.put(f.id(), new Answer(value, "claude", openEnded || !confident,
                    openEnded ? "Drafted by Claude: read before submitting" : (confident ? "Chosen by Claude" : "Claude wasn't sure")));
        }
        return out;
    }
}
