package ru.syntezis.cronctl.domain.execution;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.enums.ExecutionSource;
import ru.syntezis.cronctl.enums.RetryTrigger;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** A single task invocation tracked through the common cronctl execution lifecycle. */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskExecution {

    private final UUID executionId;
    private final String taskKey;
    private final ExecutionSource source;
    private final String nodeId;
    private final Instant createdAt;
    @Nullable
    private final Instant plannedAt;
    @Nullable
    private final UUID parentExecutionId;
    private final UUID rootExecutionId;
    private final UUID retrySeriesId;
    private final int attempt;
    @Nullable
    private final RetryTrigger retryTrigger;

    @Getter(AccessLevel.NONE)
    private final Lock stateLock = new ReentrantLock();

    private volatile TaskExecutionStatus status = TaskExecutionStatus.CREATED;
    @Nullable
    private volatile Instant queuedAt;
    @Nullable
    private volatile Instant startedAt;
    @Nullable
    private volatile Instant finishedAt;
    @Nullable
    private volatile String statusReason;
    @Nullable
    private volatile String errorType;
    @Nullable
    private volatile String errorMessage;

    @Setter
    @Nullable
    private volatile Future<?> future;

    private volatile boolean cancellationRequested;
    private volatile boolean cancellationDueToTimeout;

    public static TaskExecution create(String taskKey, ExecutionSource source, String nodeId,
                                       @Nullable Instant plannedAt, Instant createdAt) {
        UUID executionId = UUID.randomUUID();
        return new TaskExecution(
                executionId, taskKey, source, nodeId, createdAt, plannedAt,
                null, executionId, executionId, 1, null
        );
    }

    public static TaskExecution retry(TaskExecution parent, String nodeId, Instant plannedAt,
                                      Instant createdAt, RetryTrigger retryTrigger) {
        UUID executionId = UUID.randomUUID();
        boolean manual = retryTrigger == RetryTrigger.MANUAL;
        UUID retrySeriesId = manual ? executionId : parent.getRetrySeriesId();
        int attempt = manual ? 1 : parent.getAttempt() + 1;
        return new TaskExecution(
                executionId, parent.getTaskKey(), ExecutionSource.RETRY, nodeId, createdAt, plannedAt,
                parent.getExecutionId(), parent.getRootExecutionId(), retrySeriesId, attempt, retryTrigger
        );
    }

    public boolean queue(Instant transitionAt) {
        stateLock.lock();
        try {
            if (status != TaskExecutionStatus.CREATED) {
                return false;
            }

            status = TaskExecutionStatus.QUEUED;
            queuedAt = transitionAt;
            return true;
        } finally {
            stateLock.unlock();
        }
    }

    public boolean start(Instant transitionAt) {
        stateLock.lock();
        try {
            if (status != TaskExecutionStatus.QUEUED) {
                return false;
            }

            status = TaskExecutionStatus.RUNNING;
            startedAt = transitionAt;
            return true;
        } finally {
            stateLock.unlock();
        }
    }

    public void succeed(Instant transitionAt) {
        finish(TaskExecutionStatus.SUCCEEDED, transitionAt, null, null);
    }

    public void fail(Instant transitionAt, Throwable throwable) {
        finish(TaskExecutionStatus.FAILED, transitionAt, throwable.getClass().getName(), throwable.getMessage());
    }

    public boolean skip(Instant transitionAt, String reason) {
        stateLock.lock();
        try {
            if (status != TaskExecutionStatus.CREATED && status != TaskExecutionStatus.QUEUED) {
                return false;
            }

            status = TaskExecutionStatus.SKIPPED;
            statusReason = reason;
            finishedAt = transitionAt;
            return true;
        } finally {
            stateLock.unlock();
        }
    }

    public boolean requestCancellation(Instant transitionAt, boolean timedOut) {
        return requestCancellation(transitionAt, timedOut,
                timedOut ? "TIMEOUT" : "CANCELLED_BY_OPERATOR");
    }

    public boolean requestCancellation(Instant transitionAt, boolean timedOut, String reason) {
        stateLock.lock();
        try {
            if (isTerminalStatus(status)) {
                return false;
            }

            cancellationRequested = true;
            cancellationDueToTimeout = timedOut;
            statusReason = reason;
            if (status == TaskExecutionStatus.CREATED || status == TaskExecutionStatus.QUEUED) {
                status = timedOut ? TaskExecutionStatus.TIMED_OUT : TaskExecutionStatus.CANCELLED;
                finishedAt = transitionAt;
            }
            return true;
        } finally {
            stateLock.unlock();
        }
    }

    public void finishCancellation(Instant transitionAt) {
        stateLock.lock();
        try {
            if (status != TaskExecutionStatus.RUNNING) {
                return;
            }

            status = cancellationDueToTimeout ? TaskExecutionStatus.TIMED_OUT : TaskExecutionStatus.CANCELLED;
            finishedAt = transitionAt;
        } finally {
            stateLock.unlock();
        }
    }

    public boolean isTerminal() {
        return isTerminalStatus(status);
    }

    public @Nullable Long getDurationMillis() {
        Instant executionStartedAt = startedAt;
        Instant executionFinishedAt = finishedAt;
        if (executionStartedAt == null || executionFinishedAt == null) {
            return null;
        }
        return Duration.between(executionStartedAt, executionFinishedAt).toMillis();
    }

    public @Nullable Long getStartDelayMillis() {
        Instant scheduledAt = plannedAt;
        Instant executionStartedAt = startedAt;
        if (scheduledAt == null || executionStartedAt == null) {
            return null;
        }
        return Duration.between(scheduledAt, executionStartedAt).toMillis();
    }

    public void cancelFuture() {
        Future<?> executionFuture = future;
        if (executionFuture != null) {
            executionFuture.cancel(true);
        }
    }

    private void finish(TaskExecutionStatus terminalStatus, Instant transitionAt,
                        @Nullable String failureType, @Nullable String failureMessage) {
        stateLock.lock();
        try {
            if (status != TaskExecutionStatus.RUNNING) {
                return;
            }

            if (cancellationRequested) {
                status = cancellationDueToTimeout ? TaskExecutionStatus.TIMED_OUT : TaskExecutionStatus.CANCELLED;
            } else {
                status = terminalStatus;
                errorType = failureType;
                errorMessage = failureMessage;
            }
            finishedAt = transitionAt;
        } finally {
            stateLock.unlock();
        }
    }

    private boolean isTerminalStatus(TaskExecutionStatus candidate) {
        return candidate == TaskExecutionStatus.SUCCEEDED
                || candidate == TaskExecutionStatus.FAILED
                || candidate == TaskExecutionStatus.CANCELLED
                || candidate == TaskExecutionStatus.TIMED_OUT
                || candidate == TaskExecutionStatus.SKIPPED;
    }

}
