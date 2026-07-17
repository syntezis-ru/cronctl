package ru.syntezis.cronctl.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.core.NextExecutionTimeResolver;
import ru.syntezis.cronctl.core.StateToggler;
import ru.syntezis.cronctl.core.async.AsyncTaskExecutor;
import ru.syntezis.cronctl.core.async.ExecutionRegistry;
import ru.syntezis.cronctl.core.sync.BlockingTaskExecutor;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.domain.task.TaskExecutionDetails;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;
import ru.syntezis.cronctl.exception.TaskNotFoundException;
import ru.syntezis.cronctl.presentation.api.CronctlAPI;
import ru.syntezis.cronctl.presentation.dto.ExecutionStatusDto;
import ru.syntezis.cronctl.presentation.dto.ExecutionSubmittedDto;
import ru.syntezis.cronctl.presentation.dto.ExecutionsListDto;
import ru.syntezis.cronctl.presentation.dto.NextExecutionDto;
import ru.syntezis.cronctl.presentation.dto.TaskExecutionResultDto;
import ru.syntezis.cronctl.presentation.dto.TaskResponseDto;
import ru.syntezis.cronctl.presentation.dto.TasksResponseDto;
import ru.syntezis.cronctl.presentation.mapper.TaskExecutionDetailsMapper;
import ru.syntezis.cronctl.presentation.mapper.TaskExecutionMapper;
import ru.syntezis.cronctl.presentation.mapper.TaskMapper;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

/**
 * REST controller implementing the {@link CronctlAPI} contract.
 *
 * <p>Delegates task discovery and synchronous execution to {@link Cronctl}, async submission
 * to {@link AsyncTaskExecutor}, and execution queries and cancellation to {@link ExecutionRegistry}.
 * Filtering by group and tag is applied as a composable stream pipeline — when both parameters
 * are present, both conditions must match.
 */
@RestController
@RequiredArgsConstructor
public class CronctlController implements CronctlAPI {

    private final Cronctl cronctl;
    private final TaskMapper taskMapper;
    private final TaskExecutionDetailsMapper executionDetailsMapper;
    private final NextExecutionTimeResolver nextExecutionTimeResolver;
    private final StateToggler stateToggler;
    private final BlockingTaskExecutor blockingExecutor;
    private final AsyncTaskExecutor asyncExecutor;
    private final ExecutionRegistry executionRegistry;
    private final TaskExecutionMapper taskExecutionMapper;

    @Override
    public ResponseEntity<TasksResponseDto> getScheduledTasks(@Nullable String group, @Nullable String tag) {
        List<TaskResponseDto> response = cronctl.getAllTasks().stream()
                .filter(task -> group == null || group.equals(task.getGroup()))
                .filter(task -> tag == null || task.getTags().contains(tag))
                .map(task -> taskMapper.toDto(task, nextExecutionTimeResolver))
                .toList();

        return ResponseEntity.ok(TasksResponseDto.builder()
                .tasks(response)
                .build());
    }

    @Override
    public ResponseEntity<NextExecutionDto> getNextExecutionTime(UUID id) {
        Optional<Task> task = cronctl.getById(id);
        if (task.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Instant nextAt = nextExecutionTimeResolver.computeNextExecutionAt(task.get());
        return ResponseEntity.ok(NextExecutionDto.builder()
                .taskId(id)
                .nextExecutionAt(nextAt)
                .build());
    }

    @Override
    public ResponseEntity<TaskResponseDto> enableTask(UUID id) {
        Task task = getRequiredTask(id);
        Task enabledTask = stateToggler.enable(task);
        return ResponseEntity.ok(taskMapper.toDto(enabledTask, nextExecutionTimeResolver));
    }

    @Override
    public ResponseEntity<TaskResponseDto> disableTask(UUID id, boolean interrupt) {
        Task task = getRequiredTask(id);
        Task disabledTask = stateToggler.disable(task, interrupt);
        return ResponseEntity.ok(taskMapper.toDto(disabledTask, nextExecutionTimeResolver));
    }

    @Override
    public ResponseEntity<TaskExecutionResultDto> executeTask(UUID id) {
        Optional<Task> task = cronctl.getById(id);
        if (task.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TaskExecutionDetails executionDetails = blockingExecutor.executeTask(task.get());
        return ResponseEntity.ok(executionDetailsMapper.toDto(executionDetails));
    }

    @Override
    public ResponseEntity<ExecutionSubmittedDto> executeTaskAsync(UUID id) {
        Optional<Task> task = cronctl.getById(id);
        if (task.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        try {
            UUID executionId = asyncExecutor.submit(task.get());
            TaskExecution execution = executionRegistry.getById(executionId).orElseThrow();
            return ResponseEntity.accepted().body(taskExecutionMapper.toSubmittedDto(execution));
        } catch (RejectedExecutionException e) {
            return ResponseEntity.status(429).build();
        }
    }

    @Override
    public ResponseEntity<ExecutionsListDto> listExecutions(@Nullable TaskExecutionStatus status) {
        Collection<TaskExecution> executions = executionRegistry.getAll();
        if (executions.isEmpty()) {
            return ResponseEntity.ok(new ExecutionsListDto(Collections.emptyList()));
        }

        if (status != null) {
            executions = executions.stream()
                    .filter(execution -> execution.getState() == status)
                    .toList();
        }

        return ResponseEntity.ok(new ExecutionsListDto(executions.stream()
                .map(taskExecutionMapper::toStatusDto)
                .toList())
        );
    }

    /**
     * @deprecated Use {@link #executeTaskAsync(UUID)} instead.
     */
    @Deprecated(since = "0.0.3", forRemoval = true)
    @Override
    @SuppressWarnings("removal")
    public ResponseEntity<ExecutionSubmittedDto> submitExecution(UUID taskId) {
        return executeTaskAsync(taskId);
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

    private Task getRequiredTask(UUID id) {
        return cronctl.getById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task with id = " + id + " not found"));
    }
}
