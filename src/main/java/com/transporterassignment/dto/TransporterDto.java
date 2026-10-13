package com.transporterassignment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record TransporterDto(
        @NotNull(message = "transporter id is required") Long id,
        @NotBlank(message = "transporter name is required") String name,
        @NotNull(message = "laneQuotes is required") @Valid List<LaneQuoteDto> laneQuotes) {
}
