package com.jayadeep.jobradar.scoring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayadeep.jobradar.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Picks the best available scorer: Claude when an API key is configured, keyword matching otherwise.
 * If a Claude call fails (rate limit, network), the job is still scored by keywords rather than dropped.
 */
@Service
public class ScoringService {

    private static final Logger log = LoggerFactory.getLogger(ScoringService.class);

    private final FitScorer primary;
    private final KeywordFitScorer fallback;

    @Autowired
    public ScoringService(AppProperties props, RestClient.Builder builder, ObjectMapper mapper) {
        this.fallback = new KeywordFitScorer(props.skills());
        this.primary = props.claude().enabled()
                ? new ClaudeFitScorer(builder, mapper, props.claude(), props.profile())
                : fallback;
        log.info("Fit scoring uses {}", props.claude().enabled() ? "Claude (" + props.claude().model() + ")" : "keyword matching");
    }

    /** For tests. */
    ScoringService(FitScorer primary, KeywordFitScorer fallback) {
        this.primary = primary;
        this.fallback = fallback;
    }

    public FitResult score(String title, String description) {
        try {
            return primary.score(title, description);
        } catch (RuntimeException e) {
            if (primary == fallback) {
                throw e;
            }
            log.warn("Claude scoring failed for '{}', falling back to keywords: {}", title, e.getMessage());
            return fallback.score(title, description);
        }
    }
}
