package com.jayadeep.jobradar.apply;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/** One run of the apply agent against one job posting. */
@Entity
@Table(name = "job_applications")
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id")
    private Long jobId;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Ats ats;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationStatus status = ApplicationStatus.PREPARING;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "resume_path")
    private String resumePath;

    @Column(name = "tailor_notes", columnDefinition = "TEXT")
    private String tailorNotes;

    @Column(name = "report_json", columnDefinition = "TEXT")
    private String reportJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobApplication() {
    }

    public JobApplication(Long jobId, String company, String title, String url) {
        this.jobId = jobId;
        this.company = company;
        this.title = title;
        this.url = url;
        this.ats = Ats.detect(url);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getJobId() { return jobId; }
    public String getCompany() { return company; }
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public Ats getAts() { return ats; }

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getResumePath() { return resumePath; }
    public void setResumePath(String resumePath) { this.resumePath = resumePath; }

    public String getTailorNotes() { return tailorNotes; }
    public void setTailorNotes(String tailorNotes) { this.tailorNotes = tailorNotes; }

    public String getReportJson() { return reportJson; }
    public void setReportJson(String reportJson) { this.reportJson = reportJson; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
