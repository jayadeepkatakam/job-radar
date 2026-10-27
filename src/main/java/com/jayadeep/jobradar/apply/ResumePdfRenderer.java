package com.jayadeep.jobradar.apply;

import com.jayadeep.jobradar.apply.CandidateProfile.Experience;
import com.jayadeep.jobradar.apply.CandidateProfile.Resume;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a resume to a one-page US Letter PDF in the reference layout: centered name and contact line,
 * summary without a heading, TECHNICAL SKILLS, PROFESSIONAL EXPERIENCE, PROJECTS, EDUCATION,
 * CERTIFICATIONS. Shrinks type, then trims the oldest bullets, until it fits on one page.
 */
@Component
public class ResumePdfRenderer {

    /** Font size (pt) and line height pairs, tried largest first. */
    private static final double[][] SIZES = {
            {11, 1.28}, {10.5, 1.26}, {10.5, 1.2}, {10, 1.2}, {10, 1.14}, {9.5, 1.14}, {9.5, 1.08}
    };

    public byte[] render(CandidateProfile candidate, Resume resume) {
        Resume current = resume;
        for (int attempt = 0; attempt < 25; attempt++) {
            for (double[] size : SIZES) {
                byte[] pdf = toPdf(html(candidate, current, size[0], size[1]));
                if (pageCount(pdf) == 1) {
                    return pdf;
                }
            }
            Resume shorter = dropOneBullet(current);
            if (shorter == null) {
                break;
            }
            current = shorter;
        }
        // Couldn't fit on one page even with trimming; return the smallest rendering.
        double[] smallest = SIZES[SIZES.length - 1];
        return toPdf(html(candidate, current, smallest[0], smallest[1]));
    }

    /**
     * Removes one bullet, oldest job first: older jobs go down to 3 bullets before the current job loses
     * any, then everything goes down to 2. Returns null when nothing more can be trimmed.
     */
    static Resume dropOneBullet(Resume r) {
        int target = -1;
        for (int floor : new int[]{3, 2}) {
            for (int i = r.experience().size() - 1; i >= 0 && target < 0; i--) {
                int keep = i == 0 && floor == 3 ? 4 : floor;
                if (r.experience().get(i).bullets().size() > keep) {
                    target = i;
                }
            }
            if (target >= 0) {
                break;
            }
        }
        if (target < 0) {
            return null;
        }
        List<Experience> jobs = new ArrayList<>(r.experience());
        Experience e = jobs.get(target);
        jobs.set(target, new Experience(e.company(), e.location(), e.title(), e.dates(),
                e.bullets().subList(0, e.bullets().size() - 1)));
        return new Resume(r.summary(), r.skills(), jobs, r.projects(), r.education(), r.certifications());
    }

    String html(CandidateProfile c, Resume r, double fontPt, double lineHeight) {
        StringBuilder h = new StringBuilder(8_000);
        h.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"/><style>")
                .append("@page { size: letter; margin: 0.45in 0.55in 0.4in 0.55in; }")
                .append("body { font-family: sans-serif; font-size: ").append(fontPt).append("pt; line-height: ")
                .append(lineHeight).append("; color: #000; margin: 0; }")
                .append(".name { text-align: center; font-weight: bold; font-size: 20pt; line-height: 1.15; margin: 0; }")
                .append(".contact { text-align: center; margin: 2pt 0 6pt 0; }")
                .append("h2 { font-size: ").append(fontPt + 1.5).append("pt; font-weight: bold; margin: 7pt 0 3pt 0;")
                .append(" padding-bottom: 1pt; border-bottom: 0.8pt solid #000; }")
                .append("ul { margin: 1pt 0 3pt 0; padding-left: 14pt; } li { margin: 0 0 1pt 0; }")
                .append("table { width: 100%; border-collapse: collapse; } td { padding: 0; vertical-align: top; }")
                .append("td.r { text-align: right; white-space: nowrap; }")
                .append(".co { font-weight: bold; margin-top: 4pt; } .ti { font-style: italic; }")
                .append("p { margin: 0; }")
                .append("</style></head><body>");

        h.append("<p class=\"name\">").append(esc(c.fullName())).append("</p>");
        List<String> contact = new ArrayList<>();
        for (String s : new String[]{c.email(), c.phone(), c.location(), c.linkedin(), c.github()}) {
            if (s != null && !s.isBlank()) {
                contact.add(esc(s.replaceFirst("^https?://(www\\.)?", "")));
            }
        }
        h.append("<p class=\"contact\">").append(String.join(" | ", contact)).append("</p>");

        if (!r.summary().isBlank()) {
            h.append("<p>").append(esc(r.summary())).append("</p>");
        }

        if (!r.skills().isEmpty()) {
            h.append("<h2>TECHNICAL SKILLS</h2><ul>");
            r.skills().forEach(g -> h.append("<li><b>").append(esc(g.category())).append(":</b> ")
                    .append(esc(g.items())).append("</li>"));
            h.append("</ul>");
        }

        if (!r.experience().isEmpty()) {
            h.append("<h2>PROFESSIONAL EXPERIENCE</h2>");
            for (Experience e : r.experience()) {
                String where = e.location() == null || e.location().isBlank()
                        ? esc(e.company()) : esc(e.company()) + " | " + esc(e.location());
                h.append("<div class=\"co\">").append(where).append("</div>");
                h.append("<table><tr><td class=\"ti\">").append(esc(e.title())).append("</td><td class=\"r\">")
                        .append(esc(e.dates())).append("</td></tr></table>");
                bullets(h, e.bullets());
            }
        }

        if (!r.projects().isEmpty()) {
            h.append("<h2>PROJECTS</h2>");
            r.projects().forEach(p -> {
                h.append("<table><tr><td class=\"co\">").append(esc(p.name())).append("</td><td class=\"r\">")
                        .append(esc(p.dates())).append("</td></tr></table>");
                bullets(h, p.bullets());
            });
        }

        if (!r.education().isEmpty()) {
            h.append("<h2>EDUCATION</h2>");
            r.education().forEach(ed -> {
                String school = ed.school() == null ? "" : ed.school().replaceFirst("\\s*\\|.*$", "");
                h.append("<table><tr><td><b>").append(esc(ed.degree())).append("</b>")
                        .append(school.isBlank() ? "" : " — " + esc(school))
                        .append("</td><td class=\"r\">").append(esc(ed.dates())).append("</td></tr></table>");
            });
        }

        if (!r.certifications().isEmpty()) {
            h.append("<h2>CERTIFICATIONS</h2><p>");
            List<String> certs = r.certifications().stream().map(ResumePdfRenderer::esc).toList();
            h.append(String.join(" &#8226; ", certs)).append("</p>");
        }

        h.append("</body></html>");
        return h.toString();
    }

    private static void bullets(StringBuilder h, List<String> items) {
        if (items.isEmpty()) {
            return;
        }
        h.append("<ul>");
        items.forEach(b -> h.append("<li>").append(esc(b)).append("</li>"));
        h.append("</ul>");
    }

    /** HTML-escapes text, then uses numeric entities so the XML parser accepts it. */
    static String esc(String s) {
        if (s == null) {
            return "";
        }
        return HtmlUtils.htmlEscapeDecimal(s);
    }

    static byte[] toPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PDF rendering failed", e);
        }
    }

    static int pageCount(byte[] pdf) {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            throw new IllegalStateException("Could not read rendered PDF", e);
        }
    }
}
