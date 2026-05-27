package ru.syntezis.cronctl.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.core.async.AsyncTaskExecutor;
import ru.syntezis.cronctl.core.async.ExecutionRegistry;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;
import ru.syntezis.cronctl.presentation.api.AsyncExecutionAPI;
import ru.syntezis.cronctl.presentation.dto.ExecutionStatusDto;
import ru.syntezis.cronctl.presentation.dto.ExecutionSubmittedDto;
import ru.syntezis.cronctl.presentation.dto.ExecutionsListDto;
import ru.syntezis.cronctl.presentation.mapper.TaskExecutionMapper;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * REST controller implementing the {@link AsyncExecutionAPI} contract.
 *
 * <p>Delegates submission to {@link AsyncTaskExecutor} and status/cancellation queries
 * to {@link ExecutionRegistry}.
 */
@RestController
@RequiredArgsConstructor
public class AsyncExecutionController implements AsyncExecutionAPI {

    private final Cronctl cronctl;
    private final AsyncTaskExecutor asyncExecutor;
    private final ExecutionRegistry executionRegistry;
    private final TaskExecutionMapper taskExecutionMapper;

    @Override
    public ResponseEntity<ExecutionsListDto> listExecutions(TaskExecutionStatus status) {
        Collection<TaskExecution> executions = status != null
                ? executionRegistry.getByStatus(status)
                : executionRegistry.getAll();
        List<ExecutionStatusDto> dtos = executions.stream()
                .map(taskExecutionMapper::toStatusDto)
                .toList();
        return ResponseEntity.ok(new ExecutionsListDto(dtos));
    }

    @Override
    public ResponseEntity<ExecutionSubmittedDto> submitExecution(UUID taskId) {
        if (!cronctl.taskExists(taskId)) {
            return ResponseEntity.notFound().build();
        }
        try {
            Task task = cronctl.getById(taskId).orElseThrow();
            UUID executionId = asyncExecutor.submit(task);
            TaskExecution execution = executionRegistry.getById(executionId).orElseThrow();
            return ResponseEntity.accepted().body(taskExecutionMapper.toSubmittedDto(execution));
        } catch (RejectedExecutionException e) {
            return ResponseEntity.status(429).build();
        }
    }

    @Override
    public ResponseEntity<ExecutionStatusDto> getExecution(UUID executionId) {
        return executionRegistry.getById(executionId)
                .map(execution -> ResponseEntity.ok(taskExecutionMapper.toStatusDto(execution)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<Void> cancelExecution(UUID executionId) {
        return executionRegistry.cancel(executionId)
                .<ResponseEntity<Void>>map(cancelled -> cancelled
                        ? ResponseEntity.noContent().build()
                        : ResponseEntity.status(409).build())
                .orElse(ResponseEntity.notFound().build());
    }
}
