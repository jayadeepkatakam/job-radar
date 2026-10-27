package com.jayadeep.jobradar.scoring;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringServiceTest {

    @Test
    void fallsBackToKeywordsWhenPrimaryFails() {
        FitScorer failing = (title, description) -> {
            throw new IllegalStateException("429 rate limited");
        };
        ScoringService service = new ScoringService(failing, new KeywordFitScorer(List.of("Java")));

        FitResult r = service.score("Software Engineer", "Java services");

        assertThat(r.scorer()).isEqualTo("keyword");
        assertThat(r.matched()).containsExactly("Java");
    }
}
