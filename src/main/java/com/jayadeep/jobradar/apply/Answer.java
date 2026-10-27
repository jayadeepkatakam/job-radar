package com.jayadeep.jobradar.apply;

/**
 * What the agent decided for one form field.
 *
 * @param value       text to type, or the exact option label to pick; null = leave blank
 * @param source      "profile" (from candidate.yml), "claude" (drafted), "file" (resume upload) or "none"
 * @param needsReview true when you should read it before submitting (drafted text, low confidence, blanks)
 * @param note        why it was answered this way, or why it was left blank
 */
public record Answer(String value, String source, boolean needsReview, String note) {

    public static Answer fromProfile(String value) {
        return new Answer(value, "profile", false, null);
    }

    public static Answer blank(String note) {
        return new Answer(null, "none", true, note);
    }

    public boolean isBlank() {
        return value == null || value.isBlank();
    }
}
