package com.jayadeep.jobradar.api;

import com.jayadeep.jobradar.domain.Job;
import com.jayadeep.jobradar.domain.JobStatus;
import com.jayadeep.jobradar.domain.Verdict;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record JobResponse(
        Long id,
        String source,
        String company,
        String title,
        String location,
        String url,
        Instant postedAt,
        JobStatus status,
        Integer fitScore,
        Verdict verdict,
        String fitReason,
        List<String> dealBreakers,
        String notes,
        LocalDate followUp,
        Instant updatedAt,
        String description
) {

    /** Full view, including the description. */
    public static JobResponse from(Job j) {
        return of(j, j.getDescription());
    }

    /** List view: the description is left out to keep payloads small. */
    public static JobResponse summary(Job j) {
        return of(j, null);
    }

    private static JobResponse of(Job j, String description) {
        return new JobResponse(j.getId(), j.getSource(), j.getCompany(), j.getTitle(), j.getLocation(), j.getUrl(),
                j.getPostedAt(), j.getStatus(), j.getFitScore(), j.getVerdict(), j.getFitReason(),
                j.getDealBreakers(), j.getNotes(), j.getFollowUp(), j.getUpdatedAt(), description);
    }
}
