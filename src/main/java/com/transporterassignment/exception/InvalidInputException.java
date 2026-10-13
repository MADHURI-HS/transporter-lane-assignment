package com.transporterassignment.exception;

import java.util.List;

/** The request is well-formed JSON but breaks a business rule (duplicate ids, unknown lane, ...). */
public class InvalidInputException extends RuntimeException {

    private final List<String> details;

    public InvalidInputException(List<String> details) {
        super("Input data is invalid");
        this.details = details;
    }

    public List<String> getDetails() {
        return details;
    }
}
