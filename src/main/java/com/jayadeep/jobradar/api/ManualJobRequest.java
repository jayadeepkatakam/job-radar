package com.jayadeep.jobradar.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A posting found somewhere else (LinkedIn, a recruiter email) and pasted in by hand. */
public record ManualJobRequest(
        @NotBlank @Size(max = 255) String company,
        @NotBlank @Size(max = 500) String title,
        @Size(max = 500) String location,
        @Size(max = 2000) String url,
        @NotBlank @Size(max = 50_000) String description
) {
}
