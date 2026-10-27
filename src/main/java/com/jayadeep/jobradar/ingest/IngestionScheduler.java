package com.jayadeep.jobradar.ingest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs ingestion on the cron in {@code jobradar.schedule.cron}. Disable with {@code jobradar.schedule.enabled=false}. */
@Component
@ConditionalOnProperty(prefix = "jobradar.schedule", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IngestionScheduler {

    private final IngestionService ingestion;

    public IngestionScheduler(IngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @Scheduled(cron = "${jobradar.schedule.cron:0 0 8 * * MON-FRI}", zone = "${jobradar.schedule.zone:America/New_York}")
    public void run() {
        ingestion.runAll();
    }
}
