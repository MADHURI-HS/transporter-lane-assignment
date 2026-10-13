package com.transporterassignment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LaneDto(
        @NotNull(message = "lane id is required") Long id,
        @NotBlank(message = "lane origin is required") String origin,
        @NotBlank(message = "lane destination is required") String destination) {
}
