package com.jayadeep.jobradar.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "jobs", uniqueConstraints = @UniqueConstraint(columnNames = {"source", "external_id"}))
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    /** "greenhouse", "lever" or "manual". */
    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String company;

    @Column(nullable = false)
    private String title;

    private String location;

    private String url;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status = JobStatus.NEW;

    @Column(name = "fit_score")
    private Integer fitScore;

    @Enumerated(EnumType.STRING)
    private Verdict verdict;

    @Column(name = "fit_reason", columnDefinition = "TEXT")
    private String fitReason;

    @Convert(converter = StringListConverter.class)
    @Column(name = "deal_breakers", columnDefinition = "TEXT")
    private List<String> dealBreakers = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "follow_up")
    private LocalDate followUp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Job() {
        // for JPA
    }

    public Job(String source, String externalId, String company, String title) {
        this.source = source;
        this.externalId = externalId;
        this.company = company;
        this.title = title;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getSource() { return source; }
    public String getCompany() { return company; }
    public String getTitle() { return title; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public Integer getFitScore() { return fitScore; }
    public void setFitScore(Integer fitScore) { this.fitScore = fitScore; }

    public Verdict getVerdict() { return verdict; }
    public void setVerdict(Verdict verdict) { this.verdict = verdict; }

    public String getFitReason() { return fitReason; }
    public void setFitReason(String fitReason) { this.fitReason = fitReason; }

    public List<String> getDealBreakers() { return dealBreakers; }
    public void setDealBreakers(List<String> dealBreakers) {
        this.dealBreakers = dealBreakers == null ? new ArrayList<>() : new ArrayList<>(dealBreakers);
    }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDate getFollowUp() { return followUp; }
    public void setFollowUp(LocalDate followUp) { this.followUp = followUp; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
