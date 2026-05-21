package ru.syntezis.cronctl.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Captures the lifecycle and outcome of a single manual task execution.
 *
 * <p>Instances are created via {@link #prepare(UUID)} and transition through
 * {@link #execute()}, then either {@link #succeeded()} or {@link #failed(Throwable, String)}.
 * This class is not thread-safe; each execution should use its own instance.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskExecutionDetails {

    @Getter
    private final UUID executionId = UUID.randomUUID();

    @Getter
    @Nullable
    private UUID scheduledMethodId;

    @Getter
    private long executionStartMills;

    @Getter
    private long executionStartNanos;

    @Getter
    private long executionEndMills;

    @Getter
    private long executionEndNanos;

    @Getter
    private long executionDurationNanos;

    @Getter
    @Nullable
    private TaskExecutionStatus status;

    @Getter
    @Nullable
    private FailDetails failDetails;

    /**
     * Creates a new instance in {@link ru.syntezis.cronctl.enums.TaskExecutionStatus#PENDING} state,
     * ready to track the upcoming execution.
     *
     * @param scheduledMethodId UUID of the {@code @Scheduled} method that will be executed
     * @return a fresh {@code TaskExecutionDetails} instance
     */
    public static TaskExecutionDetails prepare(UUID scheduledMethodId) {
        TaskExecutionDetails details = new TaskExecutionDetails();
        details.scheduledMethodId = scheduledMethodId;
        details.status = TaskExecutionStatus.PENDING;
        return details;
    }

    /**
     * Records the execution start time and transitions status to
     * {@link ru.syntezis.cronctl.enums.TaskExecutionStatus#RUNNING}.
     * Must be called immediately before invoking the target method.
     */
    public void execute() {
        executionStartMills = System.currentTimeMillis();
        executionStartNanos = System.nanoTime();
        status = TaskExecutionStatus.RUNNING;
    }

    /**
     * Records the execution end time, calculates duration, and transitions status to
     * {@link ru.syntezis.cronctl.enums.TaskExecutionStatus#FAILED}.
     *
     * @param throwable    the exception thrown by the target method
     * @param errorMessage human-readable error description
     * @return {@code this} for chaining
     */
    public TaskExecutionDetails failed(Throwable throwable, String errorMessage) {
        executionEndMills = System.currentTimeMillis();
        executionEndNanos = System.nanoTime();
        executionDurationNanos = executionEndNanos - executionStartNanos;
        status = TaskExecutionStatus.FAILED;
        failDetails = new FailDetails(throwable, errorMessage);
        return this;
    }

    /**
     * Records the execution end time, calculates duration, and transitions status to
     * {@link ru.syntezis.cronctl.enums.TaskExecutionStatus#SUCCEEDED}.
     *
     * @return {@code this} for chaining
     */
    public TaskExecutionDetails succeeded() {
        executionEndMills = System.currentTimeMillis();
        executionEndNanos = System.nanoTime();
        executionDurationNanos = executionEndNanos - executionStartNanos;
        status = TaskExecutionStatus.SUCCEEDED;
        return this;
    }

    /**
     * Returns the execution duration converted from nanoseconds to milliseconds.
     *
     * @return duration in milliseconds, or {@code 0} if the execution has not completed yet
     */
    public long getExecutionDurationMills() {
        return TimeUnit.NANOSECONDS.toMillis(executionDurationNanos);
    }

    /**
     * Contains information about a failed task execution.
     */
    @Getter
    @AllArgsConstructor
    public static class FailDetails {

        private final Throwable throwable;
        private final String message;

    }
}
