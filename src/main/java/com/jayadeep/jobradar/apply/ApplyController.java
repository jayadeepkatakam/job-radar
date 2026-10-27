package com.jayadeep.jobradar.apply;

import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApplyController {

    private final ApplicationService service;

    public ApplyController(ApplicationService service) {
        this.service = service;
    }

    /** Either {@code jobId} (a job already in Job Radar), or {@code url + company + title + description}. */
    public record ApplyRequest(Long jobId,
                               @Size(max = 2000) String url,
                               @Size(max = 255) String company,
                               @Size(max = 500) String title,
                               @Size(max = 50_000) String description) {
    }

    public record ApplicationResponse(Long id, Long jobId, String company, String title, String url, Ats ats,
                                      ApplicationStatus status, String message, boolean hasResume,
                                      List<String> tailorNotes, List<FillResult.ReportItem> report, Instant updatedAt) {
    }

    @PostMapping("/applications")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApplicationResponse start(@RequestBody @jakarta.validation.Valid ApplyRequest req) {
        return view(service.start(new ApplicationService.StartRequest(
                req.jobId(), req.url(), req.company(), req.title(), req.description())), false);
    }

    @GetMapping("/applications")
    public List<ApplicationResponse> list() {
        return service.list().stream().map(a -> view(a, false)).toList();
    }

    @GetMapping("/applications/{id}")
    public ApplicationResponse get(@PathVariable Long id) {
        return view(service.get(id), true);
    }

    /** Fill the page that's open now (after you signed in, fixed an error, or moved to the next step). */
    @PostMapping("/applications/{id}/fill")
    public ApplicationResponse fill(@PathVariable Long id) {
        return view(service.fillCurrentPage(id), false);
    }

    @PostMapping("/applications/{id}/submitted")
    public ApplicationResponse submitted(@PathVariable Long id) {
        return view(service.markSubmitted(id), false);
    }

    @PostMapping("/applications/{id}/cancel")
    public ApplicationResponse cancel(@PathVariable Long id) {
        return view(service.cancel(id), false);
    }

    @GetMapping("/applications/{id}/resume")
    public ResponseEntity<byte[]> resume(@PathVariable Long id) throws IOException {
        JobApplication app = service.get(id);
        if (app.getResumePath() == null || !Files.exists(Path.of(app.getResumePath()))) {
            return ResponseEntity.notFound().build();
        }
        Path pdf = Path.of(app.getResumePath());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + pdf.getFileName() + "\"")
                .body(Files.readAllBytes(pdf));
    }

    @GetMapping("/profile/status")
    public Map<String, Object> profileStatus() {
        return service.profileStatus();
    }

    private ApplicationResponse view(JobApplication a, boolean withReport) {
        List<String> notes = a.getTailorNotes() == null || a.getTailorNotes().isBlank()
                ? List.of() : List.of(a.getTailorNotes().split("\n"));
        return new ApplicationResponse(a.getId(), a.getJobId(), a.getCompany(), a.getTitle(), a.getUrl(), a.getAts(),
                a.getStatus(), a.getMessage(), a.getResumePath() != null, notes,
                withReport ? service.report(a) : List.of(), a.getUpdatedAt());
    }
}
