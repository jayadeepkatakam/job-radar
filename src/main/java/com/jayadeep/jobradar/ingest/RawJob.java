package com.jayadeep.jobradar.ingest;

import java.time.Instant;

/** A posting as fetched from a job board, before screening and scoring. */
public record RawJob(
        String source,
        String externalId,
        String company,
        String title,
        String location,
        String url,
        String description,
        Instant postedAt
) {
}
