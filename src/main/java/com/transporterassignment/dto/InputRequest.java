package com.transporterassignment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InputRequest(
        @NotEmpty(message = "lanes must not be empty") @Valid List<LaneDto> lanes,
        @NotEmpty(message = "transporters must not be empty") @Valid List<TransporterDto> transporters) {
}
