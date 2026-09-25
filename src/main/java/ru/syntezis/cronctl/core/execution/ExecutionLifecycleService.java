package ru.syntezis.cronctl.core.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.core.sync.BlockingTaskExecutor;
import ru.syntezis.cronctl.core.sync.TaskInvocationResult;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.ExecutionSource;
import ru.syntezis.cronctl.enums.RetryTrigger;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Owns all execution state transitions and persists every lifecycle change. */
@Slf4j
@RequiredArgsConstructor
public class ExecutionLifecycleService {

    private final ExecutionStore executionStore;
    private final BlockingTaskExecutor blockingTaskExecutor;
    private final TaskConcurrencyController concurrencyController;
    private final RetryLifecycleHandler retryLifecycleHandler;
    private final Clock clock;
    private final String nodeId;

    public TaskExecution createQueued(String taskKey, ExecutionSource source, @Nullable Instant plannedAt) {
        Instant now = clock.instant();
        TaskExecution execution = TaskExecution.create(taskKey, source, nodeId, plannedAt, now);
        executionStore.save(execution);
        execution.queue(now);
        executionStore.save(execution);
        return execution;
    }

    public TaskExecution executeSynchronously(Task task) {
        TaskExecution execution = createQueued(task.getTaskKey(), ExecutionSource.MANUAL_SYNC, null);
        execute(execution, task);
        return execution;
    }

    public TaskExecution createRetryQueued(TaskExecution parentExecution, Instant plannedAt,
                                           RetryTrigger retryTrigger) {
        Instant now = clock.instant();
        TaskExecution execution = TaskExecution.retry(parentExecution, nodeId, plannedAt, now, retryTrigger);
        executionStore.save(execution);
        execution.queue(now);
        executionStore.save(execution);
        return execution;
    }

    public void execute(TaskExecution execution, Task task) {
        boolean acquired = false;
        try {
            acquired = acquire(task, execution);
            if (!acquired || !execution.start(clock.instant())) {
                executionStore.save(execution);
                return;
            }
            executionStore.save(execution);

            TaskInvocationResult result = blockingTaskExecutor.invoke(task, execution.getExecutionId());
            Throwable failure = null;
            if (execution.isCancellationRequested()) {
                execution.finishCancellation(clock.instant());
            } else if (result.isSucceeded()) {
                execution.succeed(clock.instant());
            } else {
                failure = Objects.requireNonNull(result.getFailure());
                execution.fail(clock.instant(), failure);
            }
            executionStore.save(execution);
            logCompletion(execution);
            if (failure != null && execution.getStatus() == TaskExecutionStatus.FAILED) {
                retryLifecycleHandler.scheduleAutomatic(task, execution, failure);
            }
        } finally {
            if (acquired) {
                concurrencyController.release(task, execution);
            }
        }
    }

    public TaskExecution startScheduled(Task task, @Nullable Instant plannedAt) {
        TaskExecution execution = createQueued(task.getTaskKey(), ExecutionSource.SCHEDULED, plannedAt);
        boolean acquired = acquire(task, execution);
        if (!acquired) {
            return execution;
        }
        if (!execution.start(clock.instant())) {
            concurrencyController.release(task, execution);
            executionStore.save(execution);
            return execution;
        }
        executionStore.save(execution);
        return execution;
    }

    public TaskExecution skipScheduled(String taskKey, @Nullable Instant plannedAt, String reason) {
        TaskExecution execution = createQueued(taskKey, ExecutionSource.SCHEDULED, plannedAt);
        skip(execution, reason);
        return execution;
    }

    public void completeScheduled(Task task, TaskExecution execution, @Nullable Throwable failure) {
        try {
            if (execution.isCancellationRequested()) {
                execution.finishCancellation(clock.instant());
            } else if (failure == null) {
                execution.succeed(clock.instant());
            } else {
                execution.fail(clock.instant(), failure);
            }
            executionStore.save(execution);
            logCompletion(execution);
            if (failure != null && execution.getStatus() == TaskExecutionStatus.FAILED) {
                retryLifecycleHandler.scheduleAutomatic(task, execution, failure);
            }
        } finally {
            concurrencyController.release(task, execution);
        }
    }

    public void skip(TaskExecution execution, String reason) {
        execution.skip(clock.instant(), reason);
        executionStore.save(execution);
        log.info("Execution {} skipped: {}", execution.getExecutionId(), reason);
    }

    public Optional<Boolean> requestCancellation(UUID executionId, boolean timedOut) {
        return executionStore.findById(executionId)
                .map(execution -> requestCancellation(execution, timedOut));
    }

    public boolean requestCancellation(TaskExecution execution, boolean timedOut) {
        return requestCancellation(execution, timedOut,
                timedOut ? "TIMEOUT" : "CANCELLED_BY_OPERATOR");
    }

    public boolean requestCancellation(TaskExecution execution, boolean timedOut, String reason) {
        boolean requested = execution.requestCancellation(clock.instant(), timedOut, reason);
        if (requested) {
            executionStore.save(execution);
            retryLifecycleHandler.cancelPending(execution);
            execution.cancelFuture();
        }
        return requested;
    }

    private boolean acquire(Task task, TaskExecution execution) {
        if (execution.isTerminal()) {
            return false;
        }
        try {
            boolean acquired = concurrencyController.acquire(task, execution,
                    previousExecution -> requestCancellation(
                            previousExecution, false, "CANCELLED_BY_CONCURRENT_EXECUTION"
                    ));
            if (!acquired) {
                skip(execution, "CONCURRENT_EXECUTION");
            }
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (!execution.isTerminal()) {
                requestCancellation(execution, false, "INTERRUPTED_WHILE_QUEUED");
            }
            return false;
        }
    }

    public void requestScheduledCancellation(String taskKey) {
        executionStore.findRunningScheduled(taskKey)
                .ifPresent(execution -> requestCancellation(execution, false));
    }

    private void logCompletion(TaskExecution execution) {
        if (execution.getErrorMessage() == null) {
            log.info("Execution {} completed with status {} in {} ms",
                    execution.getExecutionId(), execution.getStatus(), execution.getDurationMillis());
            return;
        }
        log.error("Execution {} completed with status {}: {}",
                execution.getExecutionId(), execution.getStatus(), execution.getErrorMessage());
    }

}
