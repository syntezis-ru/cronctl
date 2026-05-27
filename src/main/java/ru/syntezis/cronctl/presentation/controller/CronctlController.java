package ru.syntezis.cronctl.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.domain.task.TaskExecutionDetails;
import ru.syntezis.cronctl.presentation.api.CronctlAPI;
import ru.syntezis.cronctl.presentation.dto.TaskExecutionResultDto;
import ru.syntezis.cronctl.presentation.dto.TaskResponseDto;
import ru.syntezis.cronctl.presentation.dto.TasksResponseDto;
import ru.syntezis.cronctl.presentation.mapper.TaskExecutionDetailsMapper;
import ru.syntezis.cronctl.presentation.mapper.TaskMapper;

import java.util.List;
import java.util.UUID;

/**
 * REST controller implementing the {@link CronctlAPI} contract.
 *
 * <p>Delegates all business logic to {@link Cronctl}. Filtering by group and tag is applied
 * as a composable stream pipeline — when both parameters are present, both conditions must match.
 */
@RestController
@RequiredArgsConstructor
public class CronctlController implements CronctlAPI {

    private final Cronctl cronctl;
    private final TaskMapper taskMapper;
    private final TaskExecutionDetailsMapper executionDetailsMapper;

    @Override
    public ResponseEntity<TasksResponseDto> getScheduledTasks(@Nullable String group, @Nullable String tag) {
        List<TaskResponseDto> response = cronctl.getAllTasks().stream()
                .filter(task -> group == null || group.equals(task.getGroup()))
                .filter(task -> tag == null || task.getTags().contains(tag))
                .map(taskMapper::toDto)
                .toList();

        return ResponseEntity.ok(TasksResponseDto.builder()
                .tasks(response)
                .build());
    }

    @Override
    public ResponseEntity<TaskExecutionResultDto> executeTask(UUID id) {
        if (!cronctl.taskExists(id)) {
            return ResponseEntity.notFound()
                    .build();
        }

        TaskExecutionDetails executionDetails = cronctl.executeTaskByID(id);
        return ResponseEntity.ok(executionDetailsMapper.toDto(executionDetails));
    }
}
