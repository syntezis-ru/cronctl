package ru.syntezis.cronctl.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.domain.TaskExecutionDetails;
import ru.syntezis.cronctl.presentation.api.CronctlAPI;
import ru.syntezis.cronctl.presentation.dto.TaskExecutionResultDto;
import ru.syntezis.cronctl.presentation.dto.TaskResponseDto;
import ru.syntezis.cronctl.presentation.dto.TasksResponseDto;
import ru.syntezis.cronctl.presentation.mapper.TaskExecutionDetailsMapper;
import ru.syntezis.cronctl.presentation.mapper.TaskMapper;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CronctlController implements CronctlAPI {

    private final Cronctl cronctl;
    private final TaskMapper taskMapper;
    private final TaskExecutionDetailsMapper executionDetailsMapper;

    @Override
    public ResponseEntity<TasksResponseDto> getScheduledTasks() {
        List<TaskResponseDto> response = cronctl.getAllTasks().stream()
                .map(taskMapper::toDto)
                .toList();

        if (response.isEmpty()) {
            return ResponseEntity.ok()
                    .build();
        }

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
