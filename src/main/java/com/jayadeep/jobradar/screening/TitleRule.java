package com.jayadeep.jobradar.screening;

import java.util.Optional;
import java.util.regex.Pattern;

/** A deal-breaker that looks only at the job title, e.g. seniority levels that are out of reach. */
public record TitleRule(String reason, Pattern pattern) implements DealBreakerRule {

    public static TitleRule of(String reason, String regex) {
        return new TitleRule(reason, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    @Override
    public Optional<String> check(String title, String description) {
        return title != null && pattern.matcher(title).find() ? Optional.of(reason) : Optional.empty();
    }
}
