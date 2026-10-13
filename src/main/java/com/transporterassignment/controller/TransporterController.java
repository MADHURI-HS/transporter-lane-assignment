package com.transporterassignment.controller;

import com.transporterassignment.config.OpenApiExamples;
import com.transporterassignment.dto.AssignmentRequest;
import com.transporterassignment.dto.AssignmentResponse;
import com.transporterassignment.dto.InputRequest;
import com.transporterassignment.dto.MessageResponse;
import com.transporterassignment.service.TransporterAssignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.transporterassignment.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@RestController
@RequestMapping("/api/v1/transporters")
@Tag(name = "Transporter assignment")
public class TransporterController {

    private final TransporterAssignmentService service;

    public TransporterController(TransporterAssignmentService service) {
        this.service = service;
    }

    @PostMapping("/input")
    @Operation(summary = "Submit lanes and transporter quotes",
            description = "Stores the data set. Any data submitted earlier is replaced.")
    // on submitInput
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Input data saved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MessageResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Malformed JSON, missing fields, duplicate ids or a quote for an unknown lane",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<MessageResponse> submitInput(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(name = "Sample test case 1",
                            value = OpenApiExamples.TEST_CASE_1_INPUT)))
            @Valid @RequestBody InputRequest request) {
        service.saveInput(request);
        return ResponseEntity.ok(MessageResponse.success("Input data saved successfully."));
    }

    @PostMapping("/assignment")
    @Operation(summary = "Get the optimised transporter assignment",
            description = "Covers every lane at minimum total cost using at most maxTransporters transporters. "
                    + "Among equally cheap solutions the one using the most transporters is returned.")
    // on assign
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Optimal assignment",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AssignmentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid maxTransporters",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "No input data submitted yet",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422",
                    description = "Lanes cannot be covered within maxTransporters (or a lane has no quotes at all)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AssignmentResponse> assign(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
                    mediaType = "application/json",
                    examples = @ExampleObject(name = "Use at most 3 transporters",
                            value = OpenApiExamples.ASSIGNMENT_INPUT)))
            @Valid @RequestBody AssignmentRequest request) {
        return ResponseEntity.ok(service.assign(request.maxTransporters()));
    }
}
