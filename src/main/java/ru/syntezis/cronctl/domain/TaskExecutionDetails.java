package ru.syntezis.cronctl.domain;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TaskExecutionDetails {

    @Getter
    private final UUID executionId = UUID.randomUUID();

    @Getter
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
    private TaskExecutionStatus status;

    @Getter
    private FailDetails failDetails;

    public static TaskExecutionDetails prepare(UUID scheduledMethodId) {
        TaskExecutionDetails details = new TaskExecutionDetails();
        details.scheduledMethodId = scheduledMethodId;
        details.status = TaskExecutionStatus.PENDING;
        return details;
    }

    public void execute() {
        executionStartMills = System.currentTimeMillis();
        executionStartNanos = System.nanoTime();
        status = TaskExecutionStatus.RUNNING;
    }

    public TaskExecutionDetails failed(Throwable throwable, String errorMessage) {
        executionEndMills = System.currentTimeMillis();
        executionEndNanos = System.nanoTime();
        executionDurationNanos = executionEndNanos - executionStartNanos;
        status = TaskExecutionStatus.FAILED;
        failDetails = new FailDetails(throwable, errorMessage);
        return this;
    }

    public TaskExecutionDetails succeeded() {
        executionEndMills = System.currentTimeMillis();
        executionEndNanos = System.nanoTime();
        executionDurationNanos = executionEndNanos - executionStartNanos;
        status = TaskExecutionStatus.SUCCEEDED;
        return this;
    }

    public long getExecutionDurationMills() {
        return TimeUnit.NANOSECONDS.toMillis(executionDurationNanos);
    }

    @Getter
    @AllArgsConstructor
    public static class FailDetails {

        private final Throwable throwable;
        private final String message;

    }
}
