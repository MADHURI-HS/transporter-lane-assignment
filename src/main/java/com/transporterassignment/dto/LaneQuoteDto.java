package com.transporterassignment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LaneQuoteDto(
        @NotNull(message = "laneId is required") Long laneId,
        @NotNull(message = "quote is required") @Positive(message = "quote must be greater than 0") Long quote) {
}
