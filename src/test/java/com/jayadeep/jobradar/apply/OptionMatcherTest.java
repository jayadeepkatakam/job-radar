package com.jayadeep.jobradar.apply;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OptionMatcherTest {

    private static List<FormField.Option> opts(String... labels) {
        return Arrays.stream(labels).map(l -> new FormField.Option(null, l)).toList();
    }

    @Test
    void matchesYesNoPrefixes() {
        assertThat(OptionMatcher.match(opts("Yes, I am authorized", "No, I am not"), "No")).get()
                .extracting(FormField.Option::label).isEqualTo("No, I am not");
        assertThat(OptionMatcher.match(opts("Not sure", "No"), "No")).get()
                .extracting(FormField.Option::label).isEqualTo("No");
        assertThat(OptionMatcher.match(opts("Maybe"), "Yes")).isEmpty();
    }

    @Test
    void matchesDeclineVariants() {
        assertThat(OptionMatcher.match(opts("Male", "Female", "I prefer not to say"), "Decline to self-identify")).get()
                .extracting(FormField.Option::label).isEqualTo("I prefer not to say");
    }

    @Test
    void fallsBackToContainment() {
        assertThat(OptionMatcher.match(opts("Job Board", "Company Website"), "Company website")).get()
                .extracting(FormField.Option::label).isEqualTo("Company Website");
        assertThat(OptionMatcher.match(opts("Job Board"), "Podcast")).isEmpty();
    }
}
