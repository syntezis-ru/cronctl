package ru.syntezis.cronctl.presentation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;
import ru.syntezis.cronctl.presentation.dto.ExecutionStatusDto;
import ru.syntezis.cronctl.presentation.dto.ExecutionSubmittedDto;
import ru.syntezis.cronctl.presentation.dto.ExecutionsListDto;

import java.util.UUID;

/**
 * REST API contract for async task execution — submitting, querying, and cancelling executions.
 */
@RequestMapping("${cronctl.api.base-path}")
@Tag(name = "cronctl API", description = "HTTP API for managing @Scheduled methods")
public interface AsyncExecutionAPI {

    /** Returns all executions, optionally filtered by status. */
    @GetMapping("/executions")
    @Operation(
            summary = "List executions",
            description = "Returns all executions, optionally filtered by status."
    )
    @ApiResponse(responseCode = "200", description = "List of executions",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ExecutionsListDto.class)))
    ResponseEntity<ExecutionsListDto> listExecutions(
            @Parameter(description = "Filter by execution status; omit to return all")
            @RequestParam(name = "status", required = false) @Nullable TaskExecutionStatus status);

    /** Enqueues a registered {@code @Scheduled} task for asynchronous execution and returns an execution ID. */
    @PostMapping("/tasks/{taskId}/executions")
    @Operation(
            summary = "Submit task for async execution",
            description = "Enqueues a registered @Scheduled task for asynchronous execution. Returns immediately with an execution ID."
    )
    @ApiResponse(responseCode = "202", description = "Task accepted for execution",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ExecutionSubmittedDto.class)))
    @ApiResponse(responseCode = "404", description = "Task not found", content = @Content)
    @ApiResponse(responseCode = "429", description = "Execution queue is full", content = @Content)
    ResponseEntity<ExecutionSubmittedDto> submitExecution(
            @Parameter(description = "ID of the task to execute") @PathVariable("taskId") UUID taskId);

    /** Returns the current state and result of a previously submitted async execution. */
    @GetMapping("/executions/{executionId}")
    @Operation(
            summary = "Get execution status",
            description = "Returns the current state and result of a previously submitted async execution."
    )
    @ApiResponse(responseCode = "200", description = "Execution status",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ExecutionStatusDto.class)))
    @ApiResponse(responseCode = "404", description = "Execution not found", content = @Content)
    ResponseEntity<ExecutionStatusDto> getExecution(
            @Parameter(description = "Execution ID returned by the submit endpoint") @PathVariable("executionId") UUID executionId);

    /** Requests cancellation of a PENDING or RUNNING execution. */
    @DeleteMapping("/executions/{executionId}")
    @Operation(
            summary = "Cancel execution",
            description = "Requests cancellation of a PENDING or RUNNING execution. Returns 409 if already in a terminal state."
    )
    @ApiResponse(responseCode = "204", description = "Cancellation requested")
    @ApiResponse(responseCode = "404", description = "Execution not found", content = @Content)
    @ApiResponse(responseCode = "409", description = "Execution already completed", content = @Content)
    ResponseEntity<Void> cancelExecution(
            @Parameter(description = "ID of the execution to cancel") @PathVariable("executionId") UUID executionId);

}
