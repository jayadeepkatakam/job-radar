package com.jayadeep.jobradar.screening;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fires when the posting asks for more years of experience than the candidate is willing to stretch to.
 * Matches phrases like "8+ years", "7 or more years", "minimum of 10 years", and takes the lower
 * bound of ranges such as "5-8 years".
 */
public final class ExperienceRule implements DealBreakerRule {

    private static final Pattern YEARS = Pattern.compile(
            "\\b(\\d{1,2})(?:\\s*(?:-|–|to)\\s*\\d{1,2})?\\s*\\+?\\s*(?:or more\\s+)?years?\\b",
            Pattern.CASE_INSENSITIVE);

    private final int maxYears;

    public ExperienceRule(int maxYears) {
        this.maxYears = maxYears;
    }

    @Override
    public Optional<String> check(String title, String description) {
        if (description == null) {
            return Optional.empty();
        }
        Matcher m = YEARS.matcher(description);
        int highestMinimum = 0;
        while (m.find()) {
            int years = Integer.parseInt(m.group(1));
            // ignore things like "20 years in business" that are clearly about the company
            if (years <= 15) {
                highestMinimum = Math.max(highestMinimum, years);
            }
        }
        return highestMinimum > maxYears
                ? Optional.of("Asks for " + highestMinimum + "+ years of experience")
                : Optional.empty();
    }
}
