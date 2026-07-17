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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;
import ru.syntezis.cronctl.presentation.dto.ExecutionStatusDto;
import ru.syntezis.cronctl.presentation.dto.ExecutionSubmittedDto;
import ru.syntezis.cronctl.presentation.dto.ExecutionsListDto;
import ru.syntezis.cronctl.presentation.dto.NextExecutionDto;
import ru.syntezis.cronctl.presentation.dto.TaskExecutionResultDto;
import ru.syntezis.cronctl.presentation.dto.TaskResponseDto;
import ru.syntezis.cronctl.presentation.dto.TasksResponseDto;

import java.util.UUID;

/**
 * REST API contract for cronctl — listing, triggering, and tracking {@code @Scheduled} tasks.
 *
 * <p>The base path is resolved from {@code cronctl.api.base-path} (default: {@code /api/cronctl}).
 */
@RequestMapping("${cronctl.api.base-path}")
@Tag(name = "cronctl API", description = "HTTP API for managing @Scheduled methods")
public interface CronctlAPI {

    /**
     * Returns all registered {@code @Scheduled} tasks, optionally filtered by group and tag.
     */
    @GetMapping("/tasks")
    @Operation(
            summary = "Get scheduled tasks",
            description = "Returns registered @Scheduled tasks. Optionally filtered by group and/or tag — when both are provided, only tasks matching both conditions are returned."
    )
    @ApiResponse(responseCode = "200", description = "Scheduled tasks list",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TasksResponseDto.class)
            )
    )
    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    ResponseEntity<TasksResponseDto> getScheduledTasks(
            @Parameter(description = "Return only tasks belonging to this group") @RequestParam(name = "group", required = false) @Nullable String group,
            @Parameter(description = "Return only tasks carrying this tag") @RequestParam(name = "tag", required = false) @Nullable String tag);

    /**
     * Returns the next scheduled execution time for a cron-based task.
     */
    @GetMapping("/tasks/{id}/next-execution")
    @Operation(
            summary = "Get next execution time",
            description = "Returns the next scheduled execution time for a cron-based task. " +
                    "next_execution_at is null for fixedRate or fixedDelay tasks — next execution time " +
                    "cannot be computed without knowing the last run time."
    )
    @ApiResponse(responseCode = "200", description = "Next execution time",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = NextExecutionDto.class)
            )
    )
    @ApiResponse(responseCode = "404", description = "Task not found", content = @Content)
    ResponseEntity<NextExecutionDto> getNextExecutionTime(@PathVariable("id") UUID id);

    /**
     * Enables automatic scheduled execution for a registered task.
     */
    @PostMapping("/tasks/{id}/enable")
    @Operation(
            summary = "Enable scheduled task",
            description = "Resumes automatic scheduled execution for a task that allows toggling."
    )
    @ApiResponse(responseCode = "200", description = "Task enabled",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TaskResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Task not found", content = @Content)
    @ApiResponse(responseCode = "409", description = "Task does not allow toggling", content = @Content)
    @ApiResponse(responseCode = "500", description = "Task could not be rescheduled", content = @Content)
    ResponseEntity<TaskResponseDto> enableTask(@PathVariable("id") UUID id);

    /**
     * Disables automatic scheduled execution for a registered task.
     */
    @PostMapping("/tasks/{id}/disable")
    @Operation(
            summary = "Disable scheduled task",
            description = "Pauses automatic scheduled execution for a task that allows toggling."
    )
    @ApiResponse(responseCode = "200", description = "Task disabled",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TaskResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "Task not found", content = @Content)
    @ApiResponse(responseCode = "409", description = "Task does not allow toggling", content = @Content)
    @ApiResponse(responseCode = "500", description = "Task schedule could not be cancelled", content = @Content)
    ResponseEntity<TaskResponseDto> disableTask(
            @PathVariable("id") UUID id,
            @Parameter(description = "Whether to interrupt a currently running scheduled execution")
            @RequestParam(name = "interrupt", defaultValue = "false") boolean interrupt);

    /**
     * Manually triggers a {@code @Scheduled} method synchronously by its task ID.
     */
    @PostMapping("/tasks/{id}/execute")
    @Operation(
            summary = "Execute scheduled task",
            description = "Manually triggers a @Scheduled method synchronously by its id. " +
                    "Task-level failures are returned as 200 with status=FAILED in the body."
    )
    @ApiResponse(responseCode = "200", description = "Task execution completed (check status field for SUCCEEDED or FAILED)",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TaskExecutionResultDto.class)
            )
    )
    @ApiResponse(responseCode = "404", description = "Task not found", content = @Content)
    ResponseEntity<TaskExecutionResultDto> executeTask(@PathVariable("id") UUID id);

    /**
     * Manually triggers a {@code @Scheduled} method synchronously by its task ID.
     */
    @PostMapping("/tasks/{id}/execute-async")
    @Operation(
            summary = "Submit task for async execution",
            description = "Enqueues a registered @Scheduled task for asynchronous execution. Returns immediately with an execution ID."
    )
    @ApiResponse(responseCode = "202", description = "Task accepted for execution",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ExecutionSubmittedDto.class)))
    @ApiResponse(responseCode = "404", description = "Task not found", content = @Content)
    @ApiResponse(responseCode = "429", description = "Execution queue is full", content = @Content)
    ResponseEntity<ExecutionSubmittedDto> executeTaskAsync(
            @Parameter(description = "ID of the task to execute") @PathVariable("id") UUID id);

    /**
     * Returns all executions, optionally filtered by status.
     */
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

    /**
     * @deprecated Use {@link #executeTaskAsync(UUID)} instead.
     */
    @Deprecated(since = "0.0.4", forRemoval = true)
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

    /**
     * Returns the current state and result of a previously submitted async execution.
     */
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

    /**
     * Requests cancellation of a PENDING or RUNNING execution.
     */
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
