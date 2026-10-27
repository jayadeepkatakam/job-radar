package com.jayadeep.jobradar.scoring;

import java.util.List;

/**
 * How well a job matches the candidate.
 *
 * @param score   0-100
 * @param reason  one or two sentences explaining the score
 * @param matched skills the posting asks for that the candidate has
 * @param scorer  which scorer produced this ("keyword" or "claude")
 */
public record FitResult(int score, String reason, List<String> matched, String scorer) {

    public FitResult {
        score = Math.max(0, Math.min(100, score));
        reason = reason == null ? "" : reason;
        matched = matched == null ? List.of() : List.copyOf(matched);
    }
}
