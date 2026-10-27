package com.jayadeep.jobradar.apply;

public class ApplicationNotFoundException extends RuntimeException {

    public ApplicationNotFoundException(Long id) {
        super("Application " + id + " not found");
    }
}
