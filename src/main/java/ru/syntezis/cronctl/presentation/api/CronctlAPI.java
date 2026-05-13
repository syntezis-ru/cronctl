package ru.syntezis.cronctl.presentation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.syntezis.cronctl.presentation.dto.TaskExecutionResultDto;
import ru.syntezis.cronctl.presentation.dto.TasksResponseDto;

import java.util.UUID;

@RequestMapping("${cronctl.api.default-path}")
@Tag(name = "cronctl API", description = "HTTP API for managing @Scheduled methods")
public interface CronctlAPI {

    @GetMapping("/tasks")
    @Operation(
            summary = "Get all scheduled tasks",
            description = "Returns details of all methods annotated with @Scheduled registered in the application"
    )
    @ApiResponse(responseCode = "200", description = "Scheduled tasks list",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TasksResponseDto.class)
            )
    )
    @ApiResponse(responseCode = "500", description = "Internal server error", content = @Content)
    ResponseEntity<TasksResponseDto> getScheduledTasks();

    @PostMapping("/execute/{id}")
    @Operation(
            summary = "Execute scheduled task",
            description = "Manually triggers a @Scheduled method by its id"
    )
    @ApiResponse(responseCode = "200", description = "Task executed successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = TaskExecutionResultDto.class)
            )
    )
    @ApiResponse(responseCode = "404", description = "Task not found", content = @Content)
    @ApiResponse(responseCode = "500", description = "Task execution failed", content = @Content)
    ResponseEntity<TaskExecutionResultDto> executeTask(@PathVariable("id") UUID taskId);

}
