package com.jayadeep.jobradar.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** Greenhouse public Job Board API: https://developers.greenhouse.io/job-board.html */
@Component
public class GreenhouseClient implements JobSource {

    private final RestClient client;

    @Autowired
    public GreenhouseClient(RestClient.Builder builder) {
        this(builder, "https://boards-api.greenhouse.io");
    }

    GreenhouseClient(RestClient.Builder builder, String baseUrl) {
        this.client = builder.clone().baseUrl(baseUrl).build();
    }

    @Override
    public String name() {
        return "greenhouse";
    }

    @Override
    public List<RawJob> fetch(String boardToken, String companyName) {
        JsonNode body = client.get()
                .uri("/v1/boards/{token}/jobs?content=true", boardToken)
                .retrieve()
                .body(JsonNode.class);
        return parse(body, companyName);
    }

    static List<RawJob> parse(JsonNode body, String companyName) {
        List<RawJob> jobs = new ArrayList<>();
        if (body == null) {
            return jobs;
        }
        for (JsonNode j : body.path("jobs")) {
            jobs.add(new RawJob(
                    "greenhouse",
                    j.path("id").asText(),
                    companyName,
                    j.path("title").asText(),
                    j.path("location").path("name").asText(null),
                    j.path("absolute_url").asText(null),
                    HtmlText.toPlain(j.path("content").asText("")),
                    parseTime(j.path("updated_at").asText(null))
            ));
        }
        return jobs;
    }

    private static Instant parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
