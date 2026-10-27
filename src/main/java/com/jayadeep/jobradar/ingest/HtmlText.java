package com.jayadeep.jobradar.ingest;

import org.springframework.web.util.HtmlUtils;

/** Turns job-board HTML into plain text good enough for keyword matching and LLM prompts. */
final class HtmlText {

    private HtmlText() {
    }

    static String toPlain(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        // Greenhouse double-encodes: the HTML itself arrives entity-escaped.
        String s = HtmlUtils.htmlUnescape(html);
        s = s.replaceAll("(?i)<\\s*(br|/p|/li|/h[1-6]|/div)\\s*/?>", "\n");
        s = s.replaceAll("(?i)<\\s*li[^>]*>", "\n- ");
        s = s.replaceAll("<[^>]+>", "");
        s = HtmlUtils.htmlUnescape(s);
        s = s.replace(' ', ' ');
        s = s.replaceAll("[ \\t]+", " ");
        s = s.replaceAll("\\n\\s*\\n+", "\n\n");
        return s.strip();
    }
}
