package com.jayadeep.jobradar.apply;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayadeep.jobradar.api.JobNotFoundException;
import com.jayadeep.jobradar.domain.Job;
import com.jayadeep.jobradar.domain.JobRepository;
import com.jayadeep.jobradar.domain.JobStatus;
import com.jayadeep.jobradar.ingest.IngestionService;
import com.jayadeep.jobradar.ingest.RawJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the apply flow: tailor the resume → render the PDF → open the form in the browser → fill it →
 * stop for your review. Records each step on a {@link JobApplication} so the UI can show progress.
 */
@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final JobApplicationRepository applications;
    private final JobRepository jobs;
    private final IngestionService ingestion;
    private final CandidateProfile profile;
    private final ApplyProperties props;
    private final ResumeTailor tailor;
    private final ResumePdfRenderer renderer;
    private final FieldAnswerer answerer;
    private final BrowserAgent agent;
    private final ObjectMapper mapper;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "apply-prepare");
        t.setDaemon(true);
        return t;
    });

    public ApplicationService(JobApplicationRepository applications, JobRepository jobs, IngestionService ingestion,
                              CandidateProfile profile, ApplyProperties props, ResumeTailor tailor,
                              ResumePdfRenderer renderer, FieldAnswerer answerer, BrowserAgent agent, ObjectMapper mapper) {
        this.applications = applications;
        this.jobs = jobs;
        this.ingestion = ingestion;
        this.profile = profile;
        this.props = props;
        this.tailor = tailor;
        this.renderer = renderer;
        this.answerer = answerer;
        this.agent = agent;
        this.mapper = mapper;
    }

    public record StartRequest(Long jobId, String url, String company, String title, String description) {
    }

    /** Creates the application and starts preparing it in the background. */
    public JobApplication start(StartRequest req) {
        if (!profile.isConfigured()) {
            throw new IllegalArgumentException(
                    "Set up config/candidate.yml first (copy config/candidate.example.yml) and restart Job Radar.");
        }
        Job job = resolveJob(req);
        String url = req.url() != null && !req.url().isBlank() ? req.url().strip() : job.getUrl();
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("A link to the application page is required.");
        }
        JobApplication app = applications.save(new JobApplication(job.getId(), job.getCompany(), job.getTitle(), url));
        Long id = app.getId();
        CompletableFuture.runAsync(() -> prepareAndFill(id, job.getDescription()), worker)
                .exceptionally(t -> {
                    log.warn("Application {} failed", id, t);
                    update(id, ApplicationStatus.FAILED, rootMessage(t));
                    return null;
                });
        return app;
    }

    private Job resolveJob(StartRequest req) {
        if (req.jobId() != null) {
            return jobs.findById(req.jobId()).orElseThrow(() -> new JobNotFoundException(req.jobId()));
        }
        if (blank(req.company()) || blank(req.title()) || blank(req.description())) {
            throw new IllegalArgumentException("Company, job title and job description are required.");
        }
        RawJob raw = new RawJob("manual", UUID.randomUUID().toString(), req.company().strip(), req.title().strip(),
                null, req.url(), req.description(), null);
        return ingestion.save(raw);
    }

    void prepareAndFill(Long id, String description) {
        JobApplication app = applications.findById(id).orElseThrow();

        ResumeTailor.Tailored tailored = tailor.tailor(profile.resume(), app.getTitle(), app.getCompany(), description);
        byte[] pdf = renderer.render(profile, tailored.resume());
        Path resumePath = writeFiles(app, tailored, pdf, description);

        app.setResumePath(resumePath.toString());
        app.setTailorNotes(String.join("\n", tailored.notes()));
        app.setStatus(ApplicationStatus.FILLING);
        app.setMessage("Opening the application form…");
        applications.save(app);

        String resumeText = plainText(tailored.resume());
        agent.start(id, app.getUrl(), resumePath,
                        fields -> answerer.answer(fields, profile, app.getCompany(), app.getTitle(), description,
                                resumePath.toAbsolutePath().toString(), resumeText),
                        status -> update(id, status, statusMessage(status)))
                .thenAccept(result -> finish(id, result));
    }

    /** Fills whatever page is open now in that application's tab. */
    public JobApplication fillCurrentPage(Long id) {
        JobApplication app = get(id);
        String description = app.getJobId() == null ? "" : jobs.findById(app.getJobId()).map(Job::getDescription).orElse("");
        Path resumePath = Path.of(app.getResumePath() == null ? "" : app.getResumePath());
        String resumeText = plainText(ResumeTailor.trimmed(profile.resume()));
        update(id, ApplicationStatus.FILLING, "Filling the current page…");
        agent.resume(id, resumePath,
                        fields -> answerer.answer(fields, profile, app.getCompany(), app.getTitle(), description,
                                resumePath.toAbsolutePath().toString(), resumeText),
                        status -> update(id, status, statusMessage(status)))
                .thenAccept(result -> finish(id, result));
        return get(id);
    }

    /** You submitted it yourself: record that and move the job to Applied. */
    public JobApplication markSubmitted(Long id) {
        JobApplication app = get(id);
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setMessage("Submitted on " + LocalDate.now());
        applications.save(app);
        if (app.getJobId() != null) {
            jobs.findById(app.getJobId()).ifPresent(job -> {
                job.setStatus(JobStatus.APPLIED);
                if (job.getFollowUp() == null) {
                    job.setFollowUp(LocalDate.now().plusDays(7));
                }
                jobs.save(job);
            });
        }
        agent.close(id);
        return app;
    }

    public JobApplication cancel(Long id) {
        JobApplication app = get(id);
        app.setStatus(ApplicationStatus.CANCELLED);
        app.setMessage("Cancelled");
        applications.save(app);
        agent.close(id);
        return app;
    }

    public JobApplication get(Long id) {
        return applications.findById(id).orElseThrow(() -> new ApplicationNotFoundException(id));
    }

    public List<JobApplication> list() {
        return applications.findAllByOrderByUpdatedAtDesc();
    }

    public List<FillResult.ReportItem> report(JobApplication app) {
        if (app.getReportJson() == null) {
            return List.of();
        }
        try {
            return mapper.readValue(app.getReportJson(), new TypeReference<List<FillResult.ReportItem>>() { });
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Lists candidate.yml answers that are still empty, so the UI can warn about them. */
    public Map<String, Object> profileStatus() {
        List<String> missing = new ArrayList<>();
        if (profile.authorizedToWork() == null) {
            missing.add("authorized-to-work");
        }
        if (profile.requiresSponsorship() == null) {
            missing.add("requires-sponsorship");
        }
        if (blank(profile.linkedin())) {
            missing.add("linkedin");
        }
        if (blank(profile.city())) {
            missing.add("city");
        }
        return Map.of("configured", profile.isConfigured(), "name", profile.fullName(), "missing", missing);
    }

    private void finish(Long id, FillResult result) {
        applications.findById(id).ifPresent(app -> {
            if (app.getStatus() == ApplicationStatus.CANCELLED || app.getStatus() == ApplicationStatus.SUBMITTED) {
                return;
            }
            List<FillResult.ReportItem> all = new ArrayList<>(report(app));
            // A refill of the same page replaces earlier lines for the same questions.
            all.removeIf(old -> result.report().stream().anyMatch(n -> n.label().equals(old.label())));
            all.addAll(result.report());
            try {
                app.setReportJson(mapper.writeValueAsString(all));
            } catch (IOException e) {
                log.warn("Couldn't store fill report", e);
            }
            app.setStatus(result.status());
            app.setMessage(result.message());
            applications.save(app);
        });
    }

    private void update(Long id, ApplicationStatus status, String message) {
        applications.findById(id).ifPresent(app -> {
            app.setStatus(status);
            app.setMessage(message);
            applications.save(app);
        });
    }

    private static String statusMessage(ApplicationStatus s) {
        return switch (s) {
            case WAITING_FOR_SIGN_IN -> "Sign in or create your account in the browser window. The agent continues once you're in.";
            case FILLING -> "Filling the form…";
            default -> s.name();
        };
    }

    private Path writeFiles(JobApplication app, ResumeTailor.Tailored tailored, byte[] pdf, String description) {
        String folder = LocalDate.now() + "_" + slug(app.getCompany()) + "_" + slug(app.getTitle()) + "_" + app.getId();
        Path dir = props.outputDir().resolve(folder);
        String fileName = (slug(profile.firstName()) + "_" + slug(profile.lastName()) + "_Resume.pdf").replaceAll("^_+", "");
        try {
            Files.createDirectories(dir);
            Path resume = dir.resolve(fileName.equals("_Resume.pdf") ? "Resume.pdf" : fileName);
            Files.write(resume, pdf);
            Files.writeString(dir.resolve("job-description.txt"), description == null ? "" : description, StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("tailored-resume.json"),
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tailored), StandardCharsets.UTF_8);
            return resume;
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't save files to " + dir + ": " + e.getMessage(), e);
        }
    }

    static String plainText(CandidateProfile.Resume r) {
        StringBuilder sb = new StringBuilder(r.summary()).append("\n\nSkills:\n");
        r.skills().forEach(g -> sb.append("- ").append(g.category()).append(": ").append(g.items()).append('\n'));
        sb.append("\nExperience:\n");
        r.experience().forEach(e -> {
            sb.append(e.title()).append(", ").append(e.company()).append(" (").append(e.dates()).append(")\n");
            e.bullets().forEach(b -> sb.append("- ").append(b).append('\n'));
        });
        r.projects().forEach(p -> {
            sb.append("Project: ").append(p.name()).append('\n');
            p.bullets().forEach(b -> sb.append("- ").append(b).append('\n'));
        });
        r.education().forEach(ed -> sb.append("Education: ").append(ed.degree()).append(", ").append(ed.school()).append('\n'));
        if (!r.certifications().isEmpty()) {
            sb.append("Certifications: ").append(String.join(", ", r.certifications())).append('\n');
        }
        return sb.toString();
    }

    static String slug(String s) {
        if (s == null) {
            return "";
        }
        String out = s.replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return out.length() > 40 ? out.substring(0, 40) : out;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getMessage() == null ? c.getClass().getSimpleName() : c.getMessage();
    }
}
