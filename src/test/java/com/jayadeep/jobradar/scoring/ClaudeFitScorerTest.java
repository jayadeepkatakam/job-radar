package com.jayadeep.jobradar.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayadeep.jobradar.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaudeFitScorerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ClaudeFitScorer scorer = new ClaudeFitScorer(
            RestClient.builder(), mapper, new AppProperties.Claude("test-key", null, null), "profile");

    @Test
    void parsesPlainJsonReply() throws Exception {
        JsonNode response = mapper.readTree("""
                {"content":[{"type":"text","text":"{\\"score\\": 82, \\"reason\\": \\"Strong Java match.\\", \\"matched\\": [\\"Java\\", \\"Kafka\\"]}"}]}
                """);
        FitResult r = scorer.parse(response);
        assertThat(r.score()).isEqualTo(82);
        assertThat(r.reason()).isEqualTo("Strong Java match.");
        assertThat(r.matched()).containsExactly("Java", "Kafka");
        assertThat(r.scorer()).isEqualTo("claude");
    }

    @Test
    void toleratesCodeFencesAroundJson() throws Exception {
        JsonNode response = mapper.readTree("""
                {"content":[{"type":"text","text":"```json\\n{\\"score\\": 40, \\"reason\\": \\"Needs Go.\\", \\"matched\\": []}\\n```"}]}
                """);
        assertThat(scorer.parse(response).score()).isEqualTo(40);
    }

    @Test
    void clampsOutOfRangeScores() throws Exception {
        JsonNode response = mapper.readTree("""
                {"content":[{"type":"text","text":"{\\"score\\": 140, \\"reason\\": \\"\\", \\"matched\\": []}"}]}
                """);
        assertThat(scorer.parse(response).score()).isEqualTo(100);
    }

    @Test
    void rejectsReplyWithoutJson() throws Exception {
        JsonNode response = mapper.readTree("""
                {"content":[{"type":"text","text":"I can't help with that."}]}
                """);
        assertThatThrownBy(() -> scorer.parse(response)).isInstanceOf(IllegalStateException.class);
    }
}
