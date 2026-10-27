package com.jayadeep.jobradar.screening;

import java.util.Optional;

/** One reason a job is not worth applying to, no matter how good the fit looks. */
public interface DealBreakerRule {

    /** Returns a short human-readable reason if the posting trips this rule. */
    Optional<String> check(String title, String description);
}
