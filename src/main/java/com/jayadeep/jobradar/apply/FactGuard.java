package com.jayadeep.jobradar.apply;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps an LLM-tailored resume honest: any metric (a number such as "40%", "30+", "7") that does not
 * appear somewhere in the master resume is treated as invented, and any skill not listed in the master
 * resume is dropped.
 */
public final class FactGuard {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    private final Set<String> knownNumbers;
    private final String masterSkillsText;

    public FactGuard(CandidateProfile.Resume master) {
        StringBuilder all = new StringBuilder(master.summary()).append('\n');
        StringBuilder skills = new StringBuilder();
        master.skills().forEach(g -> {
            all.append(g.category()).append(' ').append(g.items()).append('\n');
            skills.append(g.category()).append(' ').append(g.items()).append('\n');
        });
        master.experience().forEach(e -> {
            all.append(e.company()).append(' ').append(e.title()).append(' ').append(e.dates()).append('\n');
            e.bullets().forEach(b -> all.append(b).append('\n'));
        });
        master.projects().forEach(p -> {
            all.append(p.name()).append(' ').append(p.dates()).append('\n');
            p.bullets().forEach(b -> all.append(b).append('\n'));
        });
        master.education().forEach(ed -> all.append(ed.degree()).append(' ').append(ed.school()).append('\n'));
        this.knownNumbers = numbers(all.toString());
        this.masterSkillsText = skills.toString().toLowerCase(Locale.ROOT);
    }

    /** True if every number in {@code text} also appears in the master resume. */
    public boolean numbersAreKnown(String text) {
        return knownNumbers.containsAll(numbers(text));
    }

    /** True if the skill (e.g. "Kafka") appears in the master resume's skills section. */
    public boolean skillIsKnown(String skill) {
        String s = skill.strip().toLowerCase(Locale.ROOT);
        return !s.isEmpty() && masterSkillsText.contains(s);
    }

    /** Keeps only the comma-separated skills that are in the master resume. */
    public String filterSkills(String items) {
        if (items == null) {
            return "";
        }
        List<String> kept = splitSkills(items).stream().filter(this::skillIsKnown).toList();
        return String.join(", ", kept);
    }

    /** Splits on commas that are not inside parentheses, so "Oracle (PL/SQL, SQL*Loader)" stays whole. */
    static List<String> splitSkills(String items) {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char c : items.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            }
            if (c == ',' && depth == 0) {
                if (!cur.toString().isBlank()) {
                    out.add(cur.toString().strip());
                }
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (!cur.toString().isBlank()) {
            out.add(cur.toString().strip());
        }
        return out;
    }

    private static Set<String> numbers(String text) {
        Set<String> found = new HashSet<>();
        if (text == null) {
            return found;
        }
        Matcher m = NUMBER.matcher(text);
        while (m.find()) {
            found.add(m.group().replace(",", ""));
        }
        return found;
    }
}
