package com.jayadeep.jobradar.screening;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Runs every deal-breaker rule over a posting and collects the reasons that fired. */
@Component
public class DealBreakerScanner {

    private final List<DealBreakerRule> rules;

    public DealBreakerScanner() {
        this(defaultRules());
    }

    public DealBreakerScanner(List<DealBreakerRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public List<String> scan(String title, String description) {
        return rules.stream()
                .map(rule -> rule.check(title, description))
                .flatMap(Optional::stream)
                .distinct()
                .toList();
    }

    public static List<DealBreakerRule> defaultRules() {
        return List.of(
                PatternRule.of("Requires a security clearance",
                        "\\b(security clearance|secret clearance|ts/sci|top secret|active clearance|clearance required|public trust clearance)\\b"),
                PatternRule.of("No visa sponsorship",
                        "\\b(not (?:able to |going to )?sponsor|unable to sponsor|no (?:visa )?sponsorship|"
                                + "will not (?:provide |offer )?sponsor|without (?:the need for )?(?:current or future )?sponsorship|"
                                + "does not (?:offer|provide) (?:visa )?sponsorship)"),
                PatternRule.of("U.S. citizens only",
                        "\\b(u\\.?s\\.? citizen(?:ship)? (?:is )?required|must be a u\\.?s\\.? citizen|us citizens only|"
                                + "only u\\.?s\\.? citizens)\\b"),
                PatternRule.of("Needs a certification you don't have",
                        "\\b(security\\+ (?:certification )?(?:is )?required|comptia security\\+|cissp (?:is )?required)"),
                TitleRule.of("Seniority level out of range",
                        "\\b(principal|staff|director|vp|vice president|head of|distinguished|intern(ship)?)\\b"),
                new ExperienceRule(6)
        );
    }
}
