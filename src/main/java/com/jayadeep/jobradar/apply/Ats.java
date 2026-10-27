package com.jayadeep.jobradar.apply;

import java.net.URI;
import java.util.Locale;

/** The applicant tracking system behind a job link, and where its application form lives. */
public enum Ats {
    GREENHOUSE, LEVER, ASHBY, WORKDAY, OTHER;

    public static Ats detect(String url) {
        String host = host(url);
        if (host.contains("greenhouse.io") || url.contains("gh_jid=")) {
            return GREENHOUSE;
        }
        if (host.contains("lever.co")) {
            return LEVER;
        }
        if (host.contains("ashbyhq.com")) {
            return ASHBY;
        }
        if (host.contains("myworkdayjobs.com") || host.contains("workday.com")) {
            return WORKDAY;
        }
        return OTHER;
    }

    /** Turns a job-posting link into the link of its application form, where the ATS uses a separate page. */
    public String applicationUrl(String url) {
        String u = url.strip().replaceAll("[?#].*$", "");
        return switch (this) {
            case LEVER -> u.endsWith("/apply") ? u : u.replaceAll("/+$", "") + "/apply";
            case ASHBY -> u.endsWith("/application") ? u : u.replaceAll("/+$", "") + "/application";
            default -> url.strip();
        };
    }

    /** Workday forms span several pages; the others are a single page. */
    public boolean multiPage() {
        return this == WORKDAY;
    }

    private static String host(String url) {
        try {
            String h = URI.create(url.strip()).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
