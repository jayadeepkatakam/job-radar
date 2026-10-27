package com.jayadeep.jobradar.apply;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.SelectOption;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Drives a real Chromium window with Playwright: opens the application form, uploads the tailored
 * resume, answers what it can, and stops. It never clicks Submit; you review and submit yourself.
 *
 * <p>Playwright objects are not thread-safe, so every browser call runs on one dedicated thread. The
 * browser uses a persistent profile, so sign-ins (Workday accounts) are remembered between runs.
 */
@Component
public class BrowserAgent {

    private static final Logger log = LoggerFactory.getLogger(BrowserAgent.class);

    /** Buttons the agent must never press. */
    static final Pattern SUBMIT = Pattern.compile("submit|send application|finish application", Pattern.CASE_INSENSITIVE);
    private static final Pattern APPLY_BUTTON = Pattern.compile("^\\s*apply(\\s+(now|for this job|to this job|for this position))?\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEXT_BUTTON = Pattern.compile("^\\s*(next|continue|save and continue|save & continue)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final int MAX_PAGES = 8;

    private final ApplyProperties props;
    private final ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final ExecutorService thread = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "browser-agent");
        t.setDaemon(true);
        return t;
    });
    private final Map<Long, Page> pages = new HashMap<>();
    private final String extractScript;

    private Playwright playwright;
    private BrowserContext context;

    public BrowserAgent(ApplyProperties props) {
        this.props = props;
        try {
            this.extractScript = new ClassPathResource("apply/extract-fields.js").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing apply/extract-fields.js", e);
        }
    }

    /** Opens the job link in a new tab and fills the form. */
    public CompletableFuture<FillResult> start(long applicationId, String url, Path resume,
                                               Function<List<FormField>, Map<String, Answer>> answerer,
                                               Consumer<ApplicationStatus> progress) {
        return CompletableFuture.supplyAsync(() -> {
            Page old = pages.remove(applicationId);
            if (old != null && !old.isClosed()) {
                old.close();
            }
            Page page = context().newPage();
            pages.put(applicationId, page);
            Ats ats = Ats.detect(url);
            page.navigate(ats.applicationUrl(url));
            settle(page);
            openEmbeddedForm(page);
            openFormIfNeeded(page);
            return fill(page, ats, resume, answerer, progress);
        }, thread).exceptionally(BrowserAgent::failed);
    }

    /** Fills the page that's open now, e.g. after you fixed something or moved to the next Workday step. */
    public CompletableFuture<FillResult> resume(long applicationId, Path resume,
                                                Function<List<FormField>, Map<String, Answer>> answerer,
                                                Consumer<ApplicationStatus> progress) {
        return CompletableFuture.supplyAsync(() -> {
            Page page = pages.get(applicationId);
            if (page == null || page.isClosed()) {
                return new FillResult(ApplicationStatus.FAILED, "That browser tab is closed. Start the application again.", List.of());
            }
            page.bringToFront();
            return fill(page, Ats.detect(page.url()), resume, answerer, progress);
        }, thread).exceptionally(BrowserAgent::failed);
    }

    /** Closes the tab for an application (the browser itself stays open for the next one). */
    public void close(long applicationId) {
        thread.submit(() -> {
            Page page = pages.remove(applicationId);
            if (page != null && !page.isClosed()) {
                page.close();
            }
        });
    }

    /** Runs a read-only check against an application's tab on the browser thread. For tests. */
    <T> T inspect(long applicationId, Function<Page, T> check) {
        return CompletableFuture.supplyAsync(() -> check.apply(pages.get(applicationId)), thread).join();
    }

    private FillResult fill(Page page, Ats ats, Path resume,
                            Function<List<FormField>, Map<String, Answer>> answerer,
                            Consumer<ApplicationStatus> progress) {
        if (ats == Ats.WORKDAY && !waitForSignIn(page, progress)) {
            return new FillResult(ApplicationStatus.NEEDS_INPUT,
                    "Timed out waiting for you to sign in. Sign in, then press \"Fill this page\".", List.of());
        }
        progress.accept(ApplicationStatus.FILLING);

        List<FillResult.ReportItem> report = new ArrayList<>();
        boolean resumeUploaded = false;
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            List<FormField> fields = extract(page);
            if (resumeUploaded) {
                fields = fields.stream().filter(f -> !"file".equals(f.kind())).toList();
            }
            if (fields.isEmpty()) {
                if (pageNo == 1) {
                    return new FillResult(ApplicationStatus.NEEDS_INPUT,
                            "Couldn't find an application form on this page. Open the form in the browser window, then press \"Fill this page\".",
                            report);
                }
                break;
            }
            Map<String, Answer> answers = answerer.apply(fields);
            List<FillResult.ReportItem> pageReport = new ArrayList<>();
            for (FormField f : fields) {
                Answer a = answers.getOrDefault(f.id(), Answer.blank("No answer"));
                String problem = fillField(page, f, a);
                if ("file".equals(f.kind()) && problem == null && !a.isBlank()) {
                    resumeUploaded = true;
                }
                pageReport.add(FillResult.ReportItem.of(f, a, problem));
            }
            report.addAll(pageReport);

            if (!ats.multiPage()) {
                break;
            }
            if (pageReport.stream().anyMatch(FillResult.ReportItem::blockingBlank)) {
                return new FillResult(ApplicationStatus.NEEDS_INPUT,
                        "This step has required questions only you can answer. Fill them in the browser, click Next, then press \"Fill this page\".",
                        report);
            }
            Optional<Locator> next = nextButton(page);
            if (next.isEmpty()) {
                break;
            }
            next.get().click();
            page.waitForTimeout(2500);
            if (page.locator("[data-automation-id=errorMessage]:visible, [role=alert]:visible").count() > 0) {
                return new FillResult(ApplicationStatus.NEEDS_INPUT,
                        "The form flagged an error on this step. Fix it in the browser, then press \"Fill this page\".", report);
            }
        }

        long open = report.stream().filter(FillResult.ReportItem::blockingBlank).count();
        long review = report.stream().filter(FillResult.ReportItem::needsReview).count();
        String captcha = page.locator("iframe[src*=recaptcha], iframe[src*=hcaptcha], iframe[title*=challenge]").count() > 0
                ? " There's a CAPTCHA to complete before submitting." : "";
        if (open > 0) {
            return new FillResult(ApplicationStatus.NEEDS_INPUT,
                    open + " required question(s) still need your answer. Fill them in, then review and click Submit yourself." + captcha, report);
        }
        return new FillResult(ApplicationStatus.READY_FOR_REVIEW,
                "Form filled. " + review + " answer(s) are flagged for review. Check everything, then click Submit yourself." + captcha, report);
    }

    List<FormField> extract(Page page) {
        Object raw = page.evaluate(extractScript);
        List<FormField> fields = mapper.convertValue(raw, new TypeReference<List<FormField>>() { });
        List<FormField> out = new ArrayList<>();
        for (FormField f : fields) {
            out.add("combobox".equals(f.kind()) && f.options().isEmpty() ? f.withOptions(comboOptions(page, f)) : f);
        }
        return out;
    }

    /** Opens a custom dropdown just long enough to read its choices. */
    private List<FormField.Option> comboOptions(Page page, FormField f) {
        Locator el = byId(page, f.id());
        try {
            el.click(new Locator.ClickOptions().setTimeout(3000));
            page.waitForTimeout(400);
            Locator opts = page.locator("[role=option]:visible");
            List<FormField.Option> options = opts.allInnerTexts().stream()
                    .map(String::strip).filter(s -> !s.isEmpty() && s.length() < 200)
                    .map(s -> new FormField.Option(null, s)).toList();
            page.keyboard().press("Escape");
            if (page.locator("[role=option]:visible").count() > 0) {
                el.click(new Locator.ClickOptions().setTimeout(2000));
            }
            return options;
        } catch (PlaywrightException e) {
            return List.of();
        }
    }

    /** Returns null on success, or a note explaining why the field couldn't be filled. */
    String fillField(Page page, FormField f, Answer a) {
        if (a.isBlank()) {
            return null;
        }
        String v = a.value();
        try {
            Locator el = byId(page, f.id());
            switch (f.kind()) {
                case "file" -> el.setInputFiles(Path.of(v));
                case "text", "email", "tel", "number", "url", "textarea" -> el.fill(v);
                case "date" -> {
                    if (v.matches("\\d{4}-\\d{2}-\\d{2}")) {
                        el.fill(v);
                    } else {
                        return "Date left for you";
                    }
                }
                case "select" -> el.selectOption(new SelectOption().setLabel(v));
                case "checkbox" -> {
                    if ("true".equalsIgnoreCase(v)) {
                        check(page, el);
                    }
                }
                case "radio", "checkboxGroup", "buttons" -> {
                    FormField.Option o = OptionMatcher.match(f.options(), v).orElse(null);
                    if (o == null) {
                        return "No option \"" + v + "\"";
                    }
                    Locator opt = byId(page, o.id());
                    if ("buttons".equals(f.kind())) {
                        safeClick(opt);
                    } else {
                        check(page, opt);
                    }
                }
                case "combobox" -> fillCombobox(page, el, f, v);
                default -> {
                    return "Unsupported field type " + f.kind();
                }
            }
            return null;
        } catch (PlaywrightException | IllegalStateException e) {
            log.debug("Couldn't fill '{}': {}", f.label(), e.getMessage());
            return "Couldn't fill automatically: please do this one yourself";
        }
    }

    private void fillCombobox(Page page, Locator el, FormField f, String v) {
        el.click(new Locator.ClickOptions().setTimeout(3000));
        boolean typeable = "input".equalsIgnoreCase((String) el.evaluate("e => e.tagName"));
        if (typeable) {
            el.fill(v);
            page.waitForTimeout(800);
        } else {
            page.waitForTimeout(400);
        }
        Locator options = page.locator("[role=option]:visible");
        List<FormField.Option> visible = options.allInnerTexts().stream().map(s -> new FormField.Option(null, s.strip())).toList();
        Optional<FormField.Option> match = OptionMatcher.match(visible, v);
        if (match.isPresent()) {
            Pattern exact = Pattern.compile("^\\s*" + Pattern.quote(match.get().label()) + "\\s*$");
            options.filter(new Locator.FilterOptions().setHasText(exact)).first().click();
        } else if (typeable && !visible.isEmpty()) {
            // Autocomplete (e.g. location): take the first suggestion for what we typed.
            options.first().click();
        } else if (typeable) {
            el.press("Enter");
        } else {
            page.keyboard().press("Escape");
            throw new IllegalStateException("no matching option");
        }
    }

    private static void check(Page page, Locator el) {
        try {
            el.check(new Locator.CheckOptions().setTimeout(2000));
        } catch (PlaywrightException e) {
            // Visually hidden inputs: click them from script, which React still sees as a user click.
            el.evaluate("e => e.click()");
        }
    }

    /** Clicks anything except a submit button. */
    static void safeClick(Locator el) {
        String text = (el.innerText() + " " + Optional.ofNullable(el.getAttribute("value")).orElse("")).strip();
        if (SUBMIT.matcher(text).find() || "submit".equalsIgnoreCase(el.getAttribute("type"))) {
            throw new IllegalStateException("Refusing to click a submit button: " + text);
        }
        el.click();
    }

    private Optional<Locator> nextButton(Page page) {
        Locator wd = page.locator("[data-automation-id=pageFooterNextButton]:visible");
        Locator candidate = wd.count() > 0 ? wd.first()
                : page.locator("button:visible").filter(new Locator.FilterOptions().setHasText(NEXT_BUTTON)).first();
        if (candidate.count() == 0) {
            return Optional.empty();
        }
        String text = candidate.innerText();
        // On the last step Workday's footer button says "Submit": that's where we stop.
        return SUBMIT.matcher(text).find() ? Optional.empty() : Optional.of(candidate);
    }

    private void openEmbeddedForm(Page page) {
        Locator frame = page.locator("iframe#grnhse_iframe, iframe[src*='greenhouse.io/embed']");
        if (frame.count() > 0) {
            String src = frame.first().getAttribute("src");
            if (src != null) {
                page.navigate(src);
                settle(page);
            }
        }
    }

    /** On a job description page, click through to the form (Apply → Autofill with Resume / Apply Manually). */
    private void openFormIfNeeded(Page page) {
        if (countInputs(page) >= 2) {
            return;
        }
        Locator apply = page.locator("[data-automation-id=adventureButton]:visible");
        if (apply.count() == 0) {
            apply = page.locator("a:visible, button:visible").filter(new Locator.FilterOptions().setHasText(APPLY_BUTTON));
        }
        if (apply.count() > 0) {
            Page target = page;
            safeClick(apply.first());
            page.waitForTimeout(2000);
            settle(target);
        }
        for (String id : new String[]{"autofillWithResume", "applyManually"}) {
            Locator choice = page.locator("[data-automation-id=" + id + "]:visible");
            if (choice.count() > 0) {
                safeClick(choice.first());
                page.waitForTimeout(2000);
                break;
            }
        }
    }

    private boolean waitForSignIn(Page page, Consumer<ApplicationStatus> progress) {
        long deadline = System.currentTimeMillis() + props.signInTimeoutMinutes() * 60_000L;
        boolean announced = false;
        while (System.currentTimeMillis() < deadline) {
            boolean signInShown = page.locator("input[type=password]:visible").count() > 0
                    || page.locator("[data-automation-id=signInContent]:visible, [data-automation-id=createAccountContent]:visible").count() > 0;
            if (!signInShown && countInputs(page) > 0) {
                return true;
            }
            if (!announced) {
                progress.accept(ApplicationStatus.WAITING_FOR_SIGN_IN);
                page.bringToFront();
                announced = true;
            }
            page.waitForTimeout(2000);
        }
        return false;
    }

    private static int countInputs(Page page) {
        return page.locator("input:visible:not([type=hidden]):not([type=search]), textarea:visible, select:visible").count();
    }

    private static Locator byId(Page page, String id) {
        return page.locator("[data-jr-id=\"" + id.replace("\"", "") + "\"]");
    }

    private static void settle(Page page) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(10_000));
        } catch (PlaywrightException e) {
            // Some sites never go network-idle; DOM is enough.
        }
    }

    private BrowserContext context() {
        if (context == null || context.browser() != null && !context.browser().isConnected()) {
            if (playwright == null) {
                playwright = Playwright.create();
            }
            try {
                Files.createDirectories(props.browserProfileDir());
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            context = playwright.chromium().launchPersistentContext(props.browserProfileDir(),
                    new BrowserType.LaunchPersistentContextOptions().setHeadless(props.headless()));
            context.onClose(c -> context = null);
        }
        return context;
    }

    private static FillResult failed(Throwable t) {
        Throwable cause = t.getCause() != null ? t.getCause() : t;
        log.warn("Browser agent failed", cause);
        String msg = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        if (msg.contains("Executable doesn't exist")) {
            msg = "Chromium isn't installed yet. Run: mvn exec:java -Dexec.args=\"install chromium\"";
        } else if (msg.contains("Target page, context or browser has been closed")) {
            msg = "The browser window was closed.";
        }
        return new FillResult(ApplicationStatus.FAILED, msg, List.of());
    }

    @PreDestroy
    void shutdown() {
        CompletableFuture.runAsync(() -> {
            if (context != null) {
                context.close();
            }
            if (playwright != null) {
                playwright.close();
            }
        }, thread).orTimeout(10, java.util.concurrent.TimeUnit.SECONDS).exceptionally(t -> null).join();
        thread.shutdownNow();
    }
}
