package com.jayadeep.jobradar.screening;

import java.util.Optional;
import java.util.regex.Pattern;

/** A deal-breaker that fires when a regular expression matches the posting text. */
public record PatternRule(String reason, Pattern pattern) implements DealBreakerRule {

    public static PatternRule of(String reason, String regex) {
        return new PatternRule(reason, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    @Override
    public Optional<String> check(String title, String description) {
        String text = (title == null ? "" : title) + "\n" + (description == null ? "" : description);
        return pattern.matcher(text).find() ? Optional.of(reason) : Optional.empty();
    }
}
