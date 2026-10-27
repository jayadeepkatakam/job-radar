package com.jayadeep.jobradar.apply;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayadeep.jobradar.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/** Minimal Claude Messages API client that expects a JSON object back. */
@Component
public class ClaudeClient {

    private final AppProperties.Claude settings;
    private final ObjectMapper mapper;
    private final RestClient client;

    public ClaudeClient(AppProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this.settings = props.claude();
        this.mapper = mapper;
        this.client = builder.clone()
                .baseUrl(settings.baseUrl())
                .defaultHeader("x-api-key", settings.enabled() ? settings.apiKey() : "")
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();
    }

    public boolean enabled() {
        return settings.enabled();
    }

    /** Sends one prompt and parses the first JSON object in the reply. */
    public JsonNode json(String system, String user, int maxTokens) {
        if (!enabled()) {
            throw new IllegalStateException("No ANTHROPIC_API_KEY configured");
        }
        Map<String, Object> request = Map.of(
                "model", settings.model(),
                "max_tokens", maxTokens,
                "system", system,
                "messages", List.of(Map.of("role", "user", "content", user)));
        JsonNode response = client.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        return extractJson(response, mapper);
    }

    static JsonNode extractJson(JsonNode response, ObjectMapper mapper) {
        StringBuilder text = new StringBuilder();
        if (response != null) {
            for (JsonNode block : response.path("content")) {
                if ("text".equals(block.path("type").asText())) {
                    text.append(block.path("text").asText());
                }
            }
        }
        String raw = text.toString();
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("No JSON object in Claude reply");
        }
        try {
            return mapper.readTree(raw.substring(start, end + 1));
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse Claude reply as JSON", e);
        }
    }
}
