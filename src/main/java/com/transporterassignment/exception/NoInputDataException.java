package com.transporterassignment.exception;

public class NoInputDataException extends RuntimeException {

    public NoInputDataException() {
        super("No input data found. Submit lanes and transporter quotes with POST /api/v1/transporters/input first.");
    }
}
