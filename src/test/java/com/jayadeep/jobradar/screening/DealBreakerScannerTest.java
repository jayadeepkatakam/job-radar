package com.jayadeep.jobradar.screening;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class DealBreakerScannerTest {

    private final DealBreakerScanner scanner = new DealBreakerScanner();

    @Test
    void cleanPostingHasNoDealBreakers() {
        String description = """
                We're hiring a backend engineer with 3+ years of Java and Spring Boot.
                You'll build Kafka pipelines on AWS. Visa sponsorship is available.
                """;
        assertThat(scanner.scan("Software Engineer II", description)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Candidates must hold an active Secret clearance.",
            "TS/SCI with polygraph preferred.",
            "Security clearance required for this role."
    })
    void flagsClearance(String text) {
        assertThat(scanner.scan("Software Engineer", text)).contains("Requires a security clearance");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "We are unable to sponsor visas at this time.",
            "This role does not offer visa sponsorship.",
            "Applicants must be authorized to work without current or future sponsorship.",
            "We will not sponsor employment visas."
    })
    void flagsNoSponsorship(String text) {
        assertThat(scanner.scan("Software Engineer", text)).contains("No visa sponsorship");
    }

    @Test
    void flagsCitizenshipRequirement() {
        assertThat(scanner.scan("Software Engineer", "You must be a U.S. citizen."))
                .contains("U.S. citizens only");
    }

    @Test
    void flagsSecurityPlus() {
        assertThat(scanner.scan("Software Engineer", "CompTIA Security+ within 60 days of hire."))
                .contains("Needs a certification you don't have");
    }

    @Test
    void seniorityIsCheckedInTitleOnly() {
        assertThat(scanner.scan("Staff Software Engineer", "")).contains("Seniority level out of range");
        // "staff" in the body is just a word, not a level
        assertThat(scanner.scan("Software Engineer", "You'll mentor junior staff.")).isEmpty();
    }

    @Test
    void experienceUsesTheLowerBoundOfARange() {
        ExperienceRule rule = new ExperienceRule(6);
        assertThat(rule.check("SWE", "5-8 years of experience")).isEmpty();
        assertThat(rule.check("SWE", "8+ years of backend experience")).contains("Asks for 8+ years of experience");
        assertThat(rule.check("SWE", "Minimum of 10 years in software")).isPresent();
        assertThat(rule.check("SWE", "4 or more years of Java")).isEmpty();
    }
}
