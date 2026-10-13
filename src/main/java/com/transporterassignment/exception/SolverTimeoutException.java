package com.transporterassignment.exception;

public class SolverTimeoutException extends RuntimeException {

    public SolverTimeoutException() {
        super("The optimisation did not find any valid assignment within the time limit. "
                + "Try a smaller data set or a larger maxTransporters.");
    }
}
