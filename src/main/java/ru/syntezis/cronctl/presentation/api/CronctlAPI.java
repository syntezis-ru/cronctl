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
import ru.syntezis.cronctl.presentation.dto.TaskExecutionResultDto;
import ru.syntezis.cronctl.presentation.dto.TasksResponseDto;

import java.util.UUID;

/**
 * REST API contract for cronctl — listing and manually triggering {@code @Scheduled} tasks.
 *
 * <p>The base path is resolved from {@code cronctl.api.base-path} (default: {@code /api/cronctl}).
 */
@RequestMapping("${cronctl.api.base-path}")
@Tag(name = "cronctl API", description = "HTTP API for managing @Scheduled methods")
public interface CronctlAPI {

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

}
