package com.jayadeep.jobradar.apply;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeTailorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ResumeTailor tailor = new ResumeTailor(null, mapper);

    @Test
    void keepsTruthfulEditsAndDropsInventedOnes() throws Exception {
        JsonNode reply = mapper.readTree("""
                {"summary": "Software Engineer with 4+ years in Java and Kafka who cut recovery time by 95%.",
                 "skills": [{"category": "Languages", "items": "Java, Rust, Go"},
                            {"category": "Streaming", "items": "Apache Kafka, Flink"}],
                 "experience": [
                   {"company": "Big Consulting", "bullets": [
                      "Built Kafka and Redis pipelines for real-time payments, cutting settlement delays by 50%.",
                      "Scaled the payments platform to 10 million users."]},
                   {"company": "Made Up Inc", "bullets": ["Did things."]}],
                 "projects": ["Job Radar (Java 21, Spring Boot)"]}
                """);

        ResumeTailor.Tailored t = tailor.merge(TestProfiles.resume(), reply);
        CandidateProfile.Resume r = t.resume();

        // 95% isn't in the master resume, so the master summary is kept.
        assertThat(r.summary()).isEqualTo(TestProfiles.resume().summary());
        // Rust and Flink aren't in the master skills.
        assertThat(r.skills()).extracting(CandidateProfile.SkillGroup::items).containsExactly("Java, Go", "Apache Kafka");
        // Employers, titles and dates always come from the master; unknown employers are ignored.
        assertThat(r.experience()).extracting(CandidateProfile.Experience::company)
                .containsExactly("Acme Federal", "Big Consulting", "State University", "Data Startup");
        CandidateProfile.Experience big = r.experience().get(1);
        assertThat(big.title()).isEqualTo("Software Development Engineer");
        assertThat(big.bullets()).containsExactly("Built Kafka and Redis pipelines for real-time payments, cutting settlement delays by 50%.");
        // A job Claude skipped keeps its master bullets.
        assertThat(r.experience().get(0).bullets()).hasSize(6);
        assertThat(r.projects()).hasSize(1);
        assertThat(t.notes()).anyMatch(n -> n.contains("10 million")).anyMatch(n -> n.contains("summary"));
    }

    @Test
    void factGuardKeepsParenthesizedSkillsTogether() {
        assertThat(FactGuard.splitSkills("Oracle (PL/SQL, SQL*Loader), PostgreSQL"))
                .containsExactly("Oracle (PL/SQL, SQL*Loader)", "PostgreSQL");
    }

    @Test
    void trimmedCapsBulletCounts() {
        CandidateProfile.Resume r = ResumeTailor.trimmed(TestProfiles.resume());
        assertThat(r.experience().get(0).bullets()).hasSize(6);
        assertThat(r.experience().get(1).bullets()).hasSize(5);
    }
}
