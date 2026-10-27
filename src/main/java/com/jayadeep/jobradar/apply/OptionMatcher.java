package com.jayadeep.jobradar.apply;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Picks the form option that best matches an intended answer, e.g. "Yes" → "Yes, I am authorized". */
public final class OptionMatcher {

    static final Pattern DECLINE = Pattern.compile(
            "decline|prefer not|don.?t wish|do not wish|not to (say|answer|disclose|self)|choose not|rather not|i don.?t want",
            Pattern.CASE_INSENSITIVE);

    private OptionMatcher() {
    }

    public static Optional<FormField.Option> match(List<FormField.Option> options, String wanted) {
        if (wanted == null || wanted.isBlank() || options.isEmpty()) {
            return Optional.empty();
        }
        String w = norm(wanted);

        for (FormField.Option o : options) {
            if (norm(o.label()).equals(w)) {
                return Optional.of(o);
            }
        }
        if (w.equals("yes") || w.equals("no")) {
            for (FormField.Option o : options) {
                String l = norm(o.label());
                if (l.startsWith(w + " ") || l.startsWith(w + ",") || l.equals(w)) {
                    return Optional.of(o);
                }
            }
            return Optional.empty();
        }
        if (DECLINE.matcher(wanted).find()) {
            return options.stream().filter(o -> DECLINE.matcher(o.label()).find()).findFirst();
        }
        for (FormField.Option o : options) {
            String l = norm(o.label());
            if (!l.isEmpty() && (l.contains(w) || w.contains(l))) {
                return Optional.of(o);
            }
        }
        return Optional.empty();
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+ ,]", " ").replaceAll("\\s+", " ").strip();
    }
}
