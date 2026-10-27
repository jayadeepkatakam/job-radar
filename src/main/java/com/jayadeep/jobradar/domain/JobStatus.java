package com.jayadeep.jobradar.domain;

/** Where a job sits in the application pipeline. */
public enum JobStatus {
    NEW,
    READY,
    APPLIED,
    INTERVIEW,
    OFFER,
    CLOSED
}
