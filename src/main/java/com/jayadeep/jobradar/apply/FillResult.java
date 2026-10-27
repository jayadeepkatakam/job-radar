package com.jayadeep.jobradar.apply;

import java.util.List;

/** What happened when the agent filled (part of) an application form. */
public record FillResult(ApplicationStatus status, String message, List<ReportItem> report) {

    public FillResult {
        report = report == null ? List.of() : List.copyOf(report);
    }

    /** One line of the fill report shown in the UI. */
    public record ReportItem(String label, String kind, boolean required, String value, String source,
                             boolean needsReview, String note) {

        static ReportItem of(FormField f, Answer a, String overrideNote) {
            return new ReportItem(f.label(), f.kind(), f.required(), a.value(), a.source(),
                    a.needsReview() || overrideNote != null, overrideNote != null ? overrideNote : a.note());
        }

        public boolean blockingBlank() {
            return required && (value == null || value.isBlank()) && !"Already filled (kept as is)".equals(note);
        }
    }
}
