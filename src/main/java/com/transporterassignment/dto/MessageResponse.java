package com.transporterassignment.dto;

public record MessageResponse(String status, String message) {

    public static MessageResponse success(String message) {
        return new MessageResponse("success", message);
    }
}
