package com.jayadeep.jobradar.api;

import com.jayadeep.jobradar.domain.Job;
import com.jayadeep.jobradar.domain.JobRepository;
import com.jayadeep.jobradar.domain.JobStatus;
import com.jayadeep.jobradar.ingest.IngestionService;
import com.jayadeep.jobradar.ingest.RawJob;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class JobController {

    private final JobRepository repository;
    private final IngestionService ingestion;

    public JobController(JobRepository repository, IngestionService ingestion) {
        this.repository = repository;
        this.ingestion = ingestion;
    }

    @GetMapping("/jobs")
    public List<JobResponse> list(@RequestParam(required = false) JobStatus status) {
        List<Job> jobs = status == null
                ? repository.findAllByOrderByUpdatedAtDesc()
                : repository.findByStatusOrderByFitScoreDescUpdatedAtDesc(status);
        return jobs.stream().map(JobResponse::summary).toList();
    }

    @GetMapping("/jobs/{id}")
    public JobResponse get(@PathVariable Long id) {
        return JobResponse.from(find(id));
    }

    @PatchMapping("/jobs/{id}")
    public JobResponse update(@PathVariable Long id, @Valid @RequestBody JobUpdateRequest req) {
        Job job = find(id);
        if (req.status() != null) {
            job.setStatus(req.status());
        }
        if (req.notes() != null) {
            job.setNotes(req.notes().isBlank() ? null : req.notes());
        }
        if (Boolean.TRUE.equals(req.clearFollowUp())) {
            job.setFollowUp(null);
        } else if (req.followUp() != null) {
            job.setFollowUp(req.followUp());
        }
        return JobResponse.from(repository.saveAndFlush(job));
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse create(@Valid @RequestBody ManualJobRequest req) {
        RawJob raw = new RawJob("manual", UUID.randomUUID().toString(), req.company(), req.title(),
                req.location(), req.url(), req.description(), null);
        return JobResponse.from(ingestion.save(raw));
    }

    @DeleteMapping("/jobs/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repository.delete(find(id));
    }

    /** Runs ingestion now instead of waiting for the schedule. */
    @PostMapping("/ingest/run")
    public IngestionService.IngestReport runIngestion() {
        return ingestion.runAll();
    }

    private Job find(Long id) {
        return repository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }
}
