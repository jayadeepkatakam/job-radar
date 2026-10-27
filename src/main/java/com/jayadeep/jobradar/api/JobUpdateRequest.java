package com.jayadeep.jobradar.api;

import com.jayadeep.jobradar.domain.JobStatus;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Partial update: any field left null is unchanged. Send an empty string for notes to clear them. */
public record JobUpdateRequest(
        JobStatus status,
        @Size(max = 5000) String notes,
        LocalDate followUp,
        Boolean clearFollowUp
) {
}
