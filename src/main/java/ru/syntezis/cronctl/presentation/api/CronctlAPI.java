package ru.syntezis.cronctl.presentation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.syntezis.cronctl.presentation.dto.TasksResponseDto;
import ru.syntezis.cronctl.presentation.dto.TaskExecutionResultDto;

@RequestMapping("/api/cronctl")
@Tag(name = "cronctl API", description = "Main cronctl endpoints")
public interface CronctlAPI {

    @GetMapping("/tasks")
    @Operation(summary = "Get all scheduled tasks details")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Scheduled tasks list",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TasksResponseDto.class))
            )
    })
    ResponseEntity<TasksResponseDto> getScheduledTasks();

    @PostMapping("/execute/{taskName}")
    @Operation(summary = "Execute scheduled task")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Task executed",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = TaskExecutionResultDto.class))
            )
    })
    ResponseEntity<TaskExecutionResultDto> executeTask(@PathVariable String taskName);

}
