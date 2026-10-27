package com.jayadeep.jobradar.scoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Offline scorer: counts how many of the candidate's skills the posting mentions.
 * Used when no Claude API key is configured, and as the fallback when the API call fails.
 */
public class KeywordFitScorer implements FitScorer {

    private static final Pattern BACKEND_TITLE = Pattern.compile(
            "\\b(software|backend|back-end|java|platform|api|full[- ]?stack|application) (engineer|developer)\\b",
            Pattern.CASE_INSENSITIVE);

    private final List<String> skills;

    public KeywordFitScorer(List<String> skills) {
        this.skills = List.copyOf(skills);
    }

    @Override
    public FitResult score(String title, String description) {
        String text = ((title == null ? "" : title) + " " + (description == null ? "" : description))
                .toLowerCase(Locale.ROOT);

        List<String> matched = new ArrayList<>();
        for (String skill : skills) {
            if (mentions(text, skill.toLowerCase(Locale.ROOT))) {
                matched.add(skill);
            }
        }

        // Matching ~10 of the candidate's skills is a strong signal; scale so that hits 90.
        int score = Math.min(90, matched.size() * 9);
        if (title != null && BACKEND_TITLE.matcher(title).find()) {
            score += 10;
        }

        String reason = matched.isEmpty()
                ? "None of your listed skills appear in the posting."
                : "Mentions " + matched.size() + " of your skills: " + String.join(", ", matched.subList(0, Math.min(6, matched.size()))) + ".";
        return new FitResult(score, reason, matched, "keyword");
    }

    /** Whole-word match, so "go" doesn't match "google" and "java" doesn't match "javascript". */
    static boolean mentions(String text, String skill) {
        Pattern p = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(skill) + "(?![a-z0-9])");
        return p.matcher(text).find();
    }
}
