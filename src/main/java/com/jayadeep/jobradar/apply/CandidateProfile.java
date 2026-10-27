package com.jayadeep.jobradar.apply;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Everything the apply agent knows about the candidate: contact details, standard application answers,
 * and the master resume. Loaded from {@code config/candidate.yml}, which is git-ignored so personal
 * details never end up in the repository. See {@code config/candidate.example.yml}.
 *
 * <p>Any answer left empty is treated as "don't know": the agent leaves that question blank for you.
 */
@ConfigurationProperties(prefix = "candidate")
public record CandidateProfile(
        String firstName,
        String lastName,
        String email,
        String phone,
        String city,
        String state,
        String country,
        String linkedin,
        String github,
        String website,
        String currentCompany,
        String currentTitle,
        Integer yearsExperience,
        Boolean authorizedToWork,
        Boolean requiresSponsorship,
        Boolean willingToRelocate,
        String salaryExpectation,
        String noticePeriod,
        String howDidYouHear,
        Eeo eeo,
        Resume resume
) {

    public CandidateProfile {
        eeo = eeo == null ? new Eeo(null, null, null, null) : eeo;
        resume = resume == null ? new Resume(null, null, null, null, null, null) : resume;
    }

    public String fullName() {
        return ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).strip();
    }

    public String location() {
        StringBuilder sb = new StringBuilder();
        for (String part : new String[]{city, state, country}) {
            if (part != null && !part.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(part);
            }
        }
        return sb.toString();
    }

    public boolean isConfigured() {
        return email != null && !email.isBlank() && !resume.experience().isEmpty();
    }

    /** Voluntary self-identification answers. Empty means "decline to answer". */
    public record Eeo(String gender, String race, String veteran, String disability) {
    }

    public record Resume(
            String summary,
            List<SkillGroup> skills,
            List<Experience> experience,
            List<Project> projects,
            List<Education> education,
            List<String> certifications
    ) {
        public Resume {
            summary = summary == null ? "" : summary;
            skills = skills == null ? List.of() : List.copyOf(skills);
            experience = experience == null ? List.of() : List.copyOf(experience);
            projects = projects == null ? List.of() : List.copyOf(projects);
            education = education == null ? List.of() : List.copyOf(education);
            certifications = certifications == null ? List.of() : List.copyOf(certifications);
        }
    }

    /** One TECHNICAL SKILLS line, e.g. {@code Languages: Java, Python, Go, SQL}. */
    public record SkillGroup(String category, String items) {
    }

    public record Experience(String company, String location, String title, String dates, List<String> bullets) {
        public Experience {
            bullets = bullets == null ? List.of() : List.copyOf(bullets);
        }
    }

    public record Project(String name, String dates, List<String> bullets) {
        public Project {
            bullets = bullets == null ? List.of() : List.copyOf(bullets);
        }
    }

    public record Education(String degree, String school, String dates) {
    }
}
