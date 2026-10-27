package com.jayadeep.jobradar.scoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordFitScorerTest {

    private final KeywordFitScorer scorer = new KeywordFitScorer(
            List.of("Java", "Spring Boot", "Kafka", "AWS", "PostgreSQL", "Go"));

    @Test
    void scoresMatchedSkillsAndBackendTitle() {
        FitResult r = scorer.score("Backend Engineer",
                "Build services in Java and Spring Boot, stream events with Kafka, deploy on AWS.");
        assertThat(r.matched()).containsExactly("Java", "Spring Boot", "Kafka", "AWS");
        assertThat(r.score()).isEqualTo(4 * 9 + 10);
        assertThat(r.scorer()).isEqualTo("keyword");
    }

    @Test
    void matchesWholeWordsOnly() {
        FitResult r = scorer.score("Frontend Developer", "JavaScript and Google Analytics experience.");
        assertThat(r.matched()).isEmpty();
        assertThat(r.score()).isZero();
    }

    @Test
    void scoreIsCappedAt100() {
        KeywordFitScorer many = new KeywordFitScorer(List.of("a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9", "a10", "a11"));
        FitResult r = many.score("Software Engineer", "a1 a2 a3 a4 a5 a6 a7 a8 a9 a10 a11");
        assertThat(r.score()).isEqualTo(100);
    }
}
