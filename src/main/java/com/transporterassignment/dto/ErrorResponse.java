package com.transporterassignment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(String status, String message, List<String> details) {

    public static ErrorResponse of(String message) {
        return new ErrorResponse("error", message, List.of());
    }

    public static ErrorResponse of(String message, List<String> details) {
        return new ErrorResponse("error", message, details);
    }
}
