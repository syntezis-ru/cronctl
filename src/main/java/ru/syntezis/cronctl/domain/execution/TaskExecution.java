package ru.syntezis.cronctl.domain.execution;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.domain.task.TaskExecutionDetails;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Future;

/**
 * Represents the lifecycle of a single async execution of a registered task.
 *
 * <p>State transitions are thread-safe. The intended flow is:
 * {@code PENDING} → {@code RUNNING} → {@code SUCCEEDED | FAILED | CANCELLED | TIMED_OUT}.
 */
@RequiredArgsConstructor
public class TaskExecution {

    @Getter
    private final UUID executionId = UUID.randomUUID();

    @Getter
    private final UUID taskId;

    @Getter
    private final Instant submittedAt = Instant.now();

    @Getter
    private volatile TaskExecutionStatus state = TaskExecutionStatus.PENDING;

    @Getter
    @Nullable
    private volatile Instant startedAt;

    @Getter
    @Nullable
    private volatile Instant finishedAt;

    @Getter
    @Nullable
    private volatile TaskExecutionDetails result;

    @Setter
    @Nullable
    private volatile Future<?> future;

    @Getter
    private volatile boolean cancellationRequested;

    private volatile boolean cancellationDueToTimeout;

    /**
     * Transitions from {@code PENDING} to {@code RUNNING}.
     *
     * @return {@code false} if the execution was already cancelled before it started
     */
    public synchronized boolean start() {
        if (state != TaskExecutionStatus.PENDING) {
            return false;
        }

        state = TaskExecutionStatus.RUNNING;
        startedAt = Instant.now();
        return true;
    }

    /**
     * Stores the result and transitions to {@code SUCCEEDED} or {@code FAILED}
     * based on {@link TaskExecutionDetails#getStatus()}.
     */
    public synchronized void complete(TaskExecutionDetails details) {
        if (state != TaskExecutionStatus.RUNNING) {
            return;
        }

        result = details;
        state = details.getStatus();
        finishedAt = Instant.now();
    }

    /**
     * Marks cancellation as requested. If still {@code PENDING}, transitions immediately to
     * {@code CANCELLED} or {@code TIMED_OUT}. If {@code RUNNING}, the executing thread
     * must call {@link #markCancelled()} after the method returns.
     *
     * @param timedOut {@code true} if the cancellation was triggered by a timeout
     * @return {@code false} if already in a terminal state
     */
    public synchronized boolean requestCancellation(boolean timedOut) {
        if (isTerminal()) {
            return false;
        }

        cancellationDueToTimeout = timedOut;
        cancellationRequested = true;

        if (state == TaskExecutionStatus.PENDING) {
            state = timedOut ? TaskExecutionStatus.TIMED_OUT : TaskExecutionStatus.CANCELLED;
            finishedAt = Instant.now();
        }

        return true;
    }

    /**
     * Transitions a {@code RUNNING} execution to {@code CANCELLED} or {@code TIMED_OUT}.
     * Called by the executing thread after detecting {@link #isCancellationRequested()}.
     */
    public synchronized void markCancelled() {
        if (state != TaskExecutionStatus.RUNNING) {
            return;
        }

        state = cancellationDueToTimeout ? TaskExecutionStatus.TIMED_OUT : TaskExecutionStatus.CANCELLED;
        finishedAt = Instant.now();
    }

    public boolean isTerminal() {
        TaskExecutionStatus current = state;
        return current == TaskExecutionStatus.SUCCEEDED
                || current == TaskExecutionStatus.FAILED
                || current == TaskExecutionStatus.CANCELLED
                || current == TaskExecutionStatus.TIMED_OUT;
    }

    /** Interrupts the underlying thread by cancelling the {@link Future}. */
    public void cancelFuture() {
        Future<?> f = future;
        if (f != null) {
            f.cancel(true);
        }
    }
}
