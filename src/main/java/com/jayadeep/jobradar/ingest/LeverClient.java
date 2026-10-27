package com.jayadeep.jobradar.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Lever public Postings API: https://github.com/lever/postings-api */
@Component
public class LeverClient implements JobSource {

    private final RestClient client;

    @Autowired
    public LeverClient(RestClient.Builder builder) {
        this(builder, "https://api.lever.co");
    }

    LeverClient(RestClient.Builder builder, String baseUrl) {
        this.client = builder.clone().baseUrl(baseUrl).build();
    }

    @Override
    public String name() {
        return "lever";
    }

    @Override
    public List<RawJob> fetch(String boardToken, String companyName) {
        JsonNode body = client.get()
                .uri("/v0/postings/{company}?mode=json", boardToken)
                .retrieve()
                .body(JsonNode.class);
        return parse(body, companyName);
    }

    static List<RawJob> parse(JsonNode body, String companyName) {
        List<RawJob> jobs = new ArrayList<>();
        if (body == null || !body.isArray()) {
            return jobs;
        }
        for (JsonNode p : body) {
            String description = p.path("descriptionPlain").asText("");
            StringBuilder full = new StringBuilder(description);
            for (JsonNode list : p.path("lists")) {
                full.append("\n\n").append(list.path("text").asText("")).append("\n")
                        .append(HtmlText.toPlain(list.path("content").asText("")));
            }
            full.append("\n\n").append(p.path("additionalPlain").asText(""));

            long createdMs = p.path("createdAt").asLong(0);
            jobs.add(new RawJob(
                    "lever",
                    p.path("id").asText(),
                    companyName,
                    p.path("text").asText(),
                    p.path("categories").path("location").asText(null),
                    p.path("hostedUrl").asText(null),
                    full.toString().strip(),
                    createdMs > 0 ? Instant.ofEpochMilli(createdMs) : null
            ));
        }
        return jobs;
    }
}
