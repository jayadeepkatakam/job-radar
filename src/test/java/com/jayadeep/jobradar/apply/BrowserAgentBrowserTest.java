package com.jayadeep.jobradar.apply;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayadeep.jobradar.config.AppProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fills local copies of Greenhouse-, Lever-, Ashby- and Workday-style forms in headless Chromium.
 * Needs the browser installed: {@code mvn exec:java -Dexec.args="install chromium"}, then
 * {@code mvn test -Dgroups=browser -Dsurefire.excludedGroups=}.
 */
@Tag("browser")
class BrowserAgentBrowserTest {

    @TempDir
    static Path tmp;

    private static BrowserAgent agent;
    private static Path resume;
    private static final FieldAnswerer answerer = new FieldAnswerer(
            new ClaudeClient(new AppProperties(null, null, null, null, null, null), RestClient.builder(), new ObjectMapper()),
            new ObjectMapper());
    private static final CandidateProfile profile = TestProfiles.profile(true, true);

    @BeforeAll
    static void setUp() throws Exception {
        agent = new BrowserAgent(new ApplyProperties(tmp.resolve("out"), tmp.resolve("profile"), 1, true));
        resume = tmp.resolve("Alex_Example_Resume.pdf");
        Files.write(resume, new ResumePdfRenderer().render(profile, ResumeTailor.trimmed(profile.resume())));
    }

    @AfterAll
    static void tearDown() {
        agent.shutdown();
    }

    private static String fixture(String name) {
        return Path.of("src/test/resources/forms", name).toAbsolutePath().toUri().toString();
    }

    private static Function<List<FormField>, Map<String, Answer>> answers() {
        return fields -> answerer.answer(fields, profile, "Acme", "Software Engineer", "Java role", resume.toString(), "resume");
    }

    private static String value(long id, String selector) {
        return agent.<String>inspect(id, page -> page.locator(selector).inputValue());
    }

    @Test
    void fillsGreenhouseStyleForm() {
        FillResult r = agent.start(1, fixture("greenhouse-like.html"), resume, answers(), s -> { }).join();

        assertThat(value(1, "#first_name")).isEqualTo("Alex");
        assertThat(value(1, "#last_name")).isEqualTo("Example");
        assertThat(value(1, "#email")).isEqualTo("alex@example.com");
        assertThat(value(1, "#sponsor")).isEqualTo("Yes");
        assertThat(agent.<String>inspect(1, p -> p.locator("#gender option:checked").innerText())).isEqualTo("Decline To Self Identify");
        assertThat(agent.<Boolean>inspect(1, p -> (Boolean) p.evaluate("document.getElementById('resume').files.length === 1"))).isTrue();
        // The legal attestation stays unchecked, and the "why us" question has no draft without Claude.
        assertThat(agent.<Boolean>inspect(1, p -> p.locator("#consent").isChecked())).isFalse();
        assertThat(r.report()).anyMatch(i -> i.label().startsWith("I certify") && i.needsReview());
        assertThat(r.status()).isEqualTo(ApplicationStatus.READY_FOR_REVIEW);
    }

    @Test
    void fillsLeverStyleFormAndNeverSubmits() {
        FillResult r = agent.start(2, fixture("lever-like.html"), resume, answers(), s -> { }).join();

        assertThat(value(2, "input[name=name]")).isEqualTo("Alex Example");
        assertThat(value(2, "input[name=org]")).isEqualTo("Acme Federal");
        assertThat(value(2, "input[name='urls[GitHub]']")).isEqualTo("https://github.com/alex-example");
        assertThat(agent.<Boolean>inspect(2, p -> p.locator("input[name='cards[auth]'][value=Yes]").isChecked())).isTrue();
        assertThat(value(2, "input[name='cards[years]']")).isEmpty();
        assertThat(r.report()).noneMatch(i -> i.label().toLowerCase().contains("submit"));
    }

    @Test
    void fillsAshbyStyleYesNoButtons() {
        agent.start(3, fixture("ashby-like.html"), resume, answers(), s -> { }).join();

        assertThat(value(3, "#_systemfield_name")).isEqualTo("Alex Example");
        assertThat(agent.<String>inspect(3, p -> p.locator(".yesno button[aria-pressed=true]").innerText())).isEqualTo("Yes");
    }

    @Test
    void fillsWorkdayStyleDropdownAndLeavesPersonalQuestions() {
        FillResult r = agent.start(4, fixture("workday-like.html"), resume, answers(), s -> { }).join();

        assertThat(value(4, "#fn")).isEqualTo("Alex");
        assertThat(value(4, "#ln")).isEqualTo("Example");
        assertThat(agent.<String>inspect(4, p -> p.locator("#src").innerText())).isEqualTo("Company Website");
        assertThat(agent.<Boolean>inspect(4, p -> p.locator("#pw-y").isChecked() || p.locator("#pw-n").isChecked())).isFalse();
        assertThat(r.status()).isEqualTo(ApplicationStatus.NEEDS_INPUT);
    }
}
