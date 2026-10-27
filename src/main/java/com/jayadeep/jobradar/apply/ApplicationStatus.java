package com.jayadeep.jobradar.apply;

public enum ApplicationStatus {
    /** Tailoring the resume and generating the PDF. */
    PREPARING,
    /** Browser is open; waiting for you to sign in (Workday). */
    WAITING_FOR_SIGN_IN,
    /** The agent is filling the form. */
    FILLING,
    /** Everything it could fill is filled; review the form and click Submit yourself. */
    READY_FOR_REVIEW,
    /** Some required questions need your answer before you can continue or submit. */
    NEEDS_INPUT,
    /** You told Job Radar you submitted it. */
    SUBMITTED,
    FAILED,
    CANCELLED
}
