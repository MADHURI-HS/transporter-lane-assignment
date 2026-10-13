package com.transporterassignment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * {@code optimal} is only present (as false) when the solver ran out of time and the result is the best
 * one found so far rather than a proven optimum.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssignmentResponse(
        String status,
        Long totalCost,
        List<LaneAssignmentDto> assignments,
        List<Long> selectedTransporters,
        Boolean optimal) {
}
