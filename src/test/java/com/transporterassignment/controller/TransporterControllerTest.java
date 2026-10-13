package com.transporterassignment.controller;

import com.transporterassignment.dto.AssignmentResponse;
import com.transporterassignment.dto.LaneAssignmentDto;
import com.transporterassignment.exception.InfeasibleAssignmentException;
import com.transporterassignment.exception.InvalidInputException;
import com.transporterassignment.exception.NoInputDataException;
import com.transporterassignment.service.TransporterAssignmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransporterController.class)
class TransporterControllerTest {

    private static final String VALID_INPUT = """
            {
              "lanes": [{"id": 1, "origin": "Mumbai", "destination": "Delhi"}],
              "transporters": [{"id": 1, "name": "Transporter T1", "laneQuotes": [{"laneId": 1, "quote": 5000}]}]
            }""";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TransporterAssignmentService service;

    // -------------------------------------------------------------------- input

    @Test
    @DisplayName("POST /input: valid body returns 200 and the success message")
    void inputSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Input data saved successfully."));

        verify(service).saveInput(any());
    }

    @Test
    @DisplayName("POST /input: missing and invalid fields return 400 with details")
    void inputValidationErrors() throws Exception {
        String body = """
                {
                  "lanes": [],
                  "transporters": [{"id": 1, "name": "", "laneQuotes": [{"laneId": 1, "quote": 0}]}]
                }""";

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.details").isArray());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("POST /input: malformed JSON returns 400")
    void inputMalformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("POST /input: business-rule violations return 400 with the list of problems")
    void inputBusinessRuleViolation() throws Exception {
        doThrow(new InvalidInputException(List.of("Duplicate lane id: 1")))
                .when(service).saveInput(any());

        mockMvc.perform(post("/api/v1/transporters/input")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_INPUT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("Duplicate lane id: 1"));
    }

    // --------------------------------------------------------------- assignment

    @Test
    @DisplayName("POST /assignment: returns the optimised assignment")
    void assignmentSuccess() throws Exception {
        when(service.assign(3)).thenReturn(new AssignmentResponse(
                "success", 7500L,
                List.of(new LaneAssignmentDto(1L, 1L), new LaneAssignmentDto(2L, 2L)),
                List.of(1L, 2L), null));

        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"maxTransporters\": 3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.totalCost").value(7500))
                .andExpect(jsonPath("$.assignments[1].transporterId").value(2))
                .andExpect(jsonPath("$.selectedTransporters[1]").value(2))
                .andExpect(jsonPath("$.optimal").doesNotExist());
    }

    @Test
    @DisplayName("POST /assignment: maxTransporters of 0 returns 400")
    void assignmentRejectsZero() throws Exception {
        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"maxTransporters\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0]").value("maxTransporters: maxTransporters must be at least 1"));
    }

    @Test
    @DisplayName("POST /assignment: missing maxTransporters returns 400")
    void assignmentRejectsMissingField() throws Exception {
        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /assignment: no stored data returns 409")
    void assignmentWithoutData() throws Exception {
        when(service.assign(3)).thenThrow(new NoInputDataException());

        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"maxTransporters\": 3}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("error"));
    }

    @Test
    @DisplayName("POST /assignment: lanes that cannot be covered return 422 with an explanation")
    void assignmentInfeasible() throws Exception {
        when(service.assign(2)).thenThrow(new InfeasibleAssignmentException("Covering every lane needs at least 3 transporters"));

        mockMvc.perform(post("/api/v1/transporters/assignment")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"maxTransporters\": 2}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Covering every lane needs at least 3 transporters"));
    }
}
