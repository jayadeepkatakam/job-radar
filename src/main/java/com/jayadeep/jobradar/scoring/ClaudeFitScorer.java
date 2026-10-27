package com.jayadeep.jobradar.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayadeep.jobradar.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Asks Claude (Messages API) to compare a posting against the candidate's resume summary
 * and return a JSON verdict.
 */
public class ClaudeFitScorer implements FitScorer {

    static final String API_VERSION = "2023-06-01";
    private static final int MAX_DESCRIPTION_CHARS = 12_000;

    private final RestClient client;
    private final ObjectMapper mapper;
    private final String model;
    private final String profile;

    public ClaudeFitScorer(RestClient.Builder builder, ObjectMapper mapper, AppProperties.Claude claude, String profile) {
        this.client = builder.clone()
                .baseUrl(claude.baseUrl())
                .defaultHeader("x-api-key", claude.apiKey())
                .defaultHeader("anthropic-version", API_VERSION)
                .build();
        this.mapper = mapper;
        this.model = claude.model();
        this.profile = profile;
    }

    @Override
    public FitResult score(String title, String description) {
        Map<String, Object> request = Map.of(
                "model", model,
                "max_tokens", 400,
                "system", systemPrompt(),
                "messages", List.of(Map.of("role", "user", "content", userPrompt(title, description)))
        );

        JsonNode response = client.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);

        return parse(response);
    }

    private String systemPrompt() {
        return """
                You screen job postings for one candidate. Compare the posting with the candidate's
                resume and reply with ONLY a JSON object, no prose and no code fences:
                {"score": <integer 0-100>, "reason": "<one or two sentences>", "matched": ["<skill>", ...]}
                Score 70+ only if the candidate meets most required qualifications. Penalize heavily
                for required skills or years of experience the candidate lacks.

                Candidate resume:
                """ + profile;
    }

    private static String userPrompt(String title, String description) {
        String body = description == null ? "" : description;
        if (body.length() > MAX_DESCRIPTION_CHARS) {
            body = body.substring(0, MAX_DESCRIPTION_CHARS);
        }
        return "Job title: " + title + "\n\nJob description:\n" + body;
    }

    /** Pulls the JSON verdict out of a Messages API response. Package-private for tests. */
    FitResult parse(JsonNode response) {
        if (response == null) {
            throw new IllegalStateException("Empty response from Claude");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        String raw = text.toString();
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("No JSON object in Claude reply: " + raw);
        }
        try {
            JsonNode verdict = mapper.readTree(raw.substring(start, end + 1));
            List<String> matched = new ArrayList<>();
            verdict.path("matched").forEach(n -> matched.add(n.asText()));
            return new FitResult(verdict.path("score").asInt(0), verdict.path("reason").asText(""), matched, "claude");
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse Claude reply: " + raw, e);
        }
    }
}
