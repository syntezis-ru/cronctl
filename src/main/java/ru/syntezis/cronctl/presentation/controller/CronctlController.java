package ru.syntezis.cronctl.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.presentation.api.CronctlAPI;
import ru.syntezis.cronctl.presentation.dto.TasksResponseDto;
import ru.syntezis.cronctl.presentation.dto.TaskExecutionResultDto;

@RestController
@RequiredArgsConstructor
public class CronctlController implements CronctlAPI {

    private final Cronctl cronctl;

    @Override
    public ResponseEntity<TasksResponseDto> getScheduledTasks() {
        return null;
    }

    @Override
    public ResponseEntity<TaskExecutionResultDto> executeTask(String taskName) {
        return null;
    }
}
