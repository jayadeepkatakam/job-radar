package com.jayadeep.jobradar.api;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(Long id) {
        super("Job " + id + " not found");
    }
}
