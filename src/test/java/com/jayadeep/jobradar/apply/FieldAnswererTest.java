package com.jayadeep.jobradar.apply;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayadeep.jobradar.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FieldAnswererTest {

    private final FieldAnswerer answerer = new FieldAnswerer(new ClaudeClient(
            new AppProperties(null, null, null, null, null, null), RestClient.builder(), new ObjectMapper()), new ObjectMapper());

    private static FormField text(String label) {
        return new FormField("id", "text", label, true, null, "", List.of());
    }

    private static FormField choice(String kind, String label, String... options) {
        return new FormField("id", kind, label, true, null, "",
                java.util.Arrays.stream(options).map(o -> new FormField.Option("o-" + o, o)).toList());
    }

    private Answer answer(FormField f, CandidateProfile p) {
        return answerer.byRule(f, p, "/tmp/resume.pdf");
    }

    @Test
    void fillsContactDetailsFromProfile() {
        CandidateProfile p = TestProfiles.profile(true, true);
        assertThat(answer(text("First Name"), p).value()).isEqualTo("Alex");
        assertThat(answer(text("Family Name"), p).value()).isEqualTo("Example");
        assertThat(answer(text("Full name"), p).value()).isEqualTo("Alex Example");
        assertThat(answer(text("Email"), p).value()).isEqualTo("alex@example.com");
        assertThat(answer(text("LinkedIn Profile"), p).value()).contains("linkedin.com/in/alex-example");
        assertThat(answer(text("Where are you currently located?"), p).value()).isEqualTo("Austin, TX, United States");
    }

    @Test
    void sponsorshipQuestionsUseProfileOnly() {
        CandidateProfile needsVisa = TestProfiles.profile(true, true);
        assertThat(answer(choice("select", "Will you now or in the future require sponsorship?", "Yes", "No"), needsVisa).value())
                .isEqualTo("Yes");
        // "Authorized to work WITHOUT sponsorship" flips the answer.
        assertThat(answer(choice("radio", "Are you authorized to work in the US without the need for sponsorship?", "Yes", "No"), needsVisa).value())
                .isEqualTo("No");

        CandidateProfile unknown = TestProfiles.profile(null, null);
        Answer a = answer(choice("radio", "Are you legally authorized to work in the United States?", "Yes", "No"), unknown);
        assertThat(a.isBlank()).isTrue();
        assertThat(a.needsReview()).isTrue();
    }

    @Test
    void selfIdentificationDefaultsToDecline() {
        CandidateProfile p = TestProfiles.profile(true, false);
        assertThat(answer(choice("select", "Gender", "Male", "Female", "Decline To Self Identify"), p).value())
                .isEqualTo("Decline To Self Identify");
        assertThat(answer(choice("radio", "Veteran Status", "I am a protected veteran", "I am not a protected veteran",
                "I don't wish to answer"), p).value()).isEqualTo("I don't wish to answer");
    }

    @Test
    void legalAndPersonalQuestionsAreLeftForTheCandidate() {
        CandidateProfile p = TestProfiles.profile(true, false);
        assertThat(answer(new FormField("c", "checkbox", "I certify that the information provided is true.", true, null, "", List.of()), p).isBlank()).isTrue();
        assertThat(answer(choice("radio", "Have you previously worked for this company?", "Yes", "No"), p).isBlank()).isTrue();
        assertThat(answer(choice("radio", "Do you hold an active security clearance?", "Yes", "No"), p).isBlank()).isTrue();
    }

    @Test
    void picksMatchingOptionForHowDidYouHear() {
        CandidateProfile p = TestProfiles.profile(true, false);
        assertThat(answer(choice("combobox", "How Did You Hear About Us?", "Job Board", "Company Website", "Referral"), p).value())
                .isEqualTo("Company Website");
        assertThat(answer(choice("select", "How did you hear about this job?", "LinkedIn", "Other"), p).value())
                .isEqualTo("Other");
    }

    @Test
    void uploadsResumeButNotCoverLetter() {
        CandidateProfile p = TestProfiles.profile(true, false);
        assertThat(answer(new FormField("f", "file", "Resume/CV", true, null, "", List.of()), p).value()).isEqualTo("/tmp/resume.pdf");
        assertThat(answer(new FormField("f", "file", "Cover Letter", false, null, "", List.of()), p).isBlank()).isTrue();
    }

    @Test
    void skillSpecificQuestionsAreNotAnsweredByRule() {
        CandidateProfile p = TestProfiles.profile(true, false);
        assertThat(answer(text("How many years of Java experience do you have?"), p)).isNull();
        assertThat(answer(text("Why do you want to work at Acme?"), p)).isNull();
        assertThat(answer(text("How many years of professional experience do you have?"), p).value()).isEqualTo("4");
    }
}
