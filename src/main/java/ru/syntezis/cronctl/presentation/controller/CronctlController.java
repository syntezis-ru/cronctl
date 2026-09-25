package ru.syntezis.cronctl.presentation.controller;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.core.NextExecutionTimeResolver;
import ru.syntezis.cronctl.core.StateToggler;
import ru.syntezis.cronctl.core.async.AsyncTaskExecutor;
import ru.syntezis.cronctl.core.execution.ExecutionLifecycleService;
import ru.syntezis.cronctl.core.execution.ExecutionStore;
import ru.syntezis.cronctl.core.execution.RetryService;
import ru.syntezis.cronctl.domain.execution.ExecutionPage;
import ru.syntezis.cronctl.domain.execution.ExecutionQuery;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.ExecutionSource;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;
import ru.syntezis.cronctl.exception.TaskNotFoundException;
import ru.syntezis.cronctl.presentation.api.CronctlAPI;
import ru.syntezis.cronctl.presentation.dto.ExecutionStatusDto;
import ru.syntezis.cronctl.presentation.dto.ExecutionsListDto;
import ru.syntezis.cronctl.presentation.dto.NextExecutionDto;
import ru.syntezis.cronctl.presentation.dto.RetryExecutionsResponseDto;
import ru.syntezis.cronctl.presentation.dto.TaskResponseDto;
import ru.syntezis.cronctl.presentation.dto.TasksResponseDto;
import ru.syntezis.cronctl.presentation.mapper.TaskExecutionMapper;
import ru.syntezis.cronctl.presentation.mapper.TaskMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * REST controller implementing the {@link CronctlAPI} contract.
 *
 * <p>Delegates task discovery to {@link Cronctl} and all invocation history to the common
 * execution lifecycle.
 * Filtering by group and tag is applied as a composable stream pipeline — when both parameters
 * are present, both conditions must match.
 */
@RestController
@RequiredArgsConstructor
public class CronctlController implements CronctlAPI {

    private final Cronctl cronctl;
    private final TaskMapper taskMapper;
    private final NextExecutionTimeResolver nextExecutionTimeResolver;
    private final StateToggler stateToggler;
    private final ExecutionLifecycleService lifecycleService;
    private final AsyncTaskExecutor asyncExecutor;
    private final ExecutionStore executionStore;
    private final RetryService retryService;
    private final TaskExecutionMapper taskExecutionMapper;

    @Override
    public ResponseEntity<TasksResponseDto> getScheduledTasks(@Nullable String group, @Nullable String tag) {
        List<TaskResponseDto> response = cronctl.getAllTasks().stream()
                .filter(task -> group == null || group.equals(task.getGroup()))
                .filter(task -> tag == null || task.getTags().contains(tag))
                .map(task -> taskMapper.toDto(task, nextExecutionTimeResolver, executionStore))
                .toList();

        return ResponseEntity.ok(TasksResponseDto.builder()
                .tasks(response)
                .build());
    }

    @Override
    public ResponseEntity<NextExecutionDto> getNextExecutionTime(String taskKey) {
        Optional<Task> task = cronctl.getById(taskKey);
        if (task.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Instant nextAt = nextExecutionTimeResolver.computeNextExecutionAt(task.get());
        return ResponseEntity.ok(NextExecutionDto.builder()
                .taskKey(taskKey)
                .nextExecutionAt(nextAt)
                .build());
    }

    @Override
    public ResponseEntity<TaskResponseDto> enableTask(String taskKey) {
        Task task = getRequiredTask(taskKey);
        Task enabledTask = stateToggler.enable(task);
        return ResponseEntity.ok(taskMapper.toDto(enabledTask, nextExecutionTimeResolver, executionStore));
    }

    @Override
    public ResponseEntity<TaskResponseDto> disableTask(String taskKey, boolean interrupt) {
        Task task = getRequiredTask(taskKey);
        Task disabledTask = stateToggler.disable(task, interrupt);
        return ResponseEntity.ok(taskMapper.toDto(disabledTask, nextExecutionTimeResolver, executionStore));
    }

    @Override
    public ResponseEntity<ExecutionStatusDto> executeTask(String taskKey) {
        Optional<Task> task = cronctl.getById(taskKey);
        if (task.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TaskExecution execution = lifecycleService.executeSynchronously(task.get());
        ExecutionStatusDto response = toDto(execution);
        return execution.getStatus() == TaskExecutionStatus.SKIPPED
                ? ResponseEntity.status(429).body(response)
                : ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<ExecutionStatusDto> executeTaskAsync(String taskKey) {
        Optional<Task> task = cronctl.getById(taskKey);
        if (task.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        TaskExecution execution = asyncExecutor.submit(task.get());
        ExecutionStatusDto response = toDto(execution);
        return execution.getStatus() == TaskExecutionStatus.SKIPPED
                && "QUEUE_REJECTED".equals(execution.getStatusReason())
                ? ResponseEntity.status(429).body(response)
                : ResponseEntity.accepted().body(response);
    }

    @Override
    public ResponseEntity<ExecutionsListDto> listExecutions(@Nullable String taskKey,
                                                            @Nullable TaskExecutionStatus status,
                                                            @Nullable ExecutionSource source,
                                                            @Nullable String nodeId,
                                                            @Nullable Instant from,
                                                            @Nullable Instant to,
                                                            int page,
                                                            int size) {
        ExecutionPage executions = executionStore.findAll(ExecutionQuery.builder()
                .taskKey(taskKey)
                .status(status)
                .source(source)
                .nodeId(nodeId)
                .from(from)
                .to(to)
                .page(page)
                .size(size)
                .build());
        return ResponseEntity.ok(ExecutionsListDto.builder()
                .executions(executions.getExecutions().stream().map(this::toDto).toList())
                .total(executions.getTotal())
                .page(executions.getPage())
                .size(executions.getSize())
                .hasNext(executions.hasNext())
                .build());
    }

    /**
     * @deprecated Use {@link #executeTaskAsync(String)} instead.
     */
    @Deprecated(since = "0.0.3", forRemoval = true)
    @Override
    @SuppressWarnings("removal")
    public ResponseEntity<ExecutionStatusDto> submitExecution(String taskKey) {
        return executeTaskAsync(taskKey);
    }

    @Override
    public ResponseEntity<ExecutionStatusDto> getExecution(UUID executionId) {
        return executionStore.findById(executionId)
                .map(execution -> ResponseEntity.ok(toDto(execution)))
                .orElse(ResponseEntity.notFound().build());
    }

    @Override
    public ResponseEntity<ExecutionStatusDto> retryExecution(UUID executionId) {
        return executionStore.findById(executionId)
                .flatMap(parentExecution -> cronctl.getById(parentExecution.getTaskKey())
                        .map(task -> retryTaskExecution(task, parentExecution)))
                .orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<ExecutionStatusDto> retryTaskExecution(Task task, TaskExecution parentExecution) {
        return retryService.retry(task, parentExecution)
                .map(retry -> ResponseEntity.accepted().body(toDto(retry)))
                .orElse(ResponseEntity.status(409).build());
    }

    @Override
    public ResponseEntity<RetryExecutionsResponseDto> retryAllFailed() {
        List<TaskExecution> retries = retryService.retryAllFailed();
        return ResponseEntity.ok(RetryExecutionsResponseDto.builder()
                .executions(retries.stream().map(this::toDto).toList())
                .submitted(retries.size())
                .build());
    }

    @Override
    public ResponseEntity<Void> cancelExecution(UUID executionId) {
        return executionStore.findById(executionId)
                .<ResponseEntity<Void>>map(execution -> (execution.getSource() == ExecutionSource.MANUAL_ASYNC
                        || execution.getSource() == ExecutionSource.RETRY)
                        && lifecycleService.requestCancellation(execution, false)
                                ? ResponseEntity.noContent().build()
                                : ResponseEntity.status(409).build())
                .orElse(ResponseEntity.notFound().build());
    }

    private Task getRequiredTask(String taskKey) {
        return cronctl.getById(taskKey)
                .orElseThrow(() -> new TaskNotFoundException("Task with key = " + taskKey + " not found"));
    }

    private ExecutionStatusDto toDto(TaskExecution execution) {
        return taskExecutionMapper.toDto(execution, retryService.isRetryable(execution));
    }
}
