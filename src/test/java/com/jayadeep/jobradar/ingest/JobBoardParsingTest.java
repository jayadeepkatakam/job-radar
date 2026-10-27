package com.jayadeep.jobradar.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobBoardParsingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesGreenhouseBoard() throws Exception {
        String json = """
                {"jobs":[{
                  "id": 12345,
                  "title": "Software Engineer, Payments",
                  "location": {"name": "New York, NY"},
                  "absolute_url": "https://boards.greenhouse.io/acme/jobs/12345",
                  "updated_at": "2026-10-20T14:03:11-04:00",
                  "content": "&lt;p&gt;Build &lt;strong&gt;Java&lt;/strong&gt; services.&lt;/p&gt;&lt;ul&gt;&lt;li&gt;Kafka&lt;/li&gt;&lt;/ul&gt;"
                }]}
                """;
        List<RawJob> jobs = GreenhouseClient.parse(mapper.readTree(json), "Acme");

        assertThat(jobs).hasSize(1);
        RawJob j = jobs.get(0);
        assertThat(j.source()).isEqualTo("greenhouse");
        assertThat(j.externalId()).isEqualTo("12345");
        assertThat(j.company()).isEqualTo("Acme");
        assertThat(j.location()).isEqualTo("New York, NY");
        assertThat(j.postedAt()).isEqualTo(Instant.parse("2026-10-20T18:03:11Z"));
        assertThat(j.description()).contains("Build Java services.").contains("- Kafka").doesNotContain("<");
    }

    @Test
    void parsesLeverPostings() throws Exception {
        String json = """
                [{
                  "id": "abc-123",
                  "text": "Backend Engineer",
                  "categories": {"location": "Remote - US"},
                  "hostedUrl": "https://jobs.lever.co/acme/abc-123",
                  "createdAt": 1760968991000,
                  "descriptionPlain": "Join our platform team.",
                  "lists": [{"text": "Requirements", "content": "<li>3+ years Java</li>"}],
                  "additionalPlain": "We sponsor visas."
                }]
                """;
        List<RawJob> jobs = LeverClient.parse(mapper.readTree(json), "Acme");

        assertThat(jobs).hasSize(1);
        RawJob j = jobs.get(0);
        assertThat(j.externalId()).isEqualTo("abc-123");
        assertThat(j.title()).isEqualTo("Backend Engineer");
        assertThat(j.location()).isEqualTo("Remote - US");
        assertThat(j.postedAt()).isEqualTo(Instant.ofEpochMilli(1760968991000L));
        assertThat(j.description()).contains("Join our platform team.", "Requirements", "3+ years Java", "We sponsor visas.");
    }

    @Test
    void emptyOrMissingBodiesYieldNoJobs() throws Exception {
        assertThat(GreenhouseClient.parse(null, "Acme")).isEmpty();
        assertThat(GreenhouseClient.parse(mapper.readTree("{}"), "Acme")).isEmpty();
        assertThat(LeverClient.parse(mapper.readTree("{\"ok\":false}"), "Acme")).isEmpty();
    }
}
