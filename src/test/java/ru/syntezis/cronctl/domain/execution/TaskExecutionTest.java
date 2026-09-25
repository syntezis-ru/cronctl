package ru.syntezis.cronctl.domain.execution;

import org.junit.jupiter.api.Test;
import ru.syntezis.cronctl.enums.ExecutionSource;
import ru.syntezis.cronctl.enums.RetryTrigger;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TaskExecutionTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-20T10:00:00Z");

    @Test
    void start_QueuedExecution_RunningWithTimingData() {
        // Given
        final long expected = 300L;
        Instant plannedAt = CREATED_AT.minusMillis(250);
        TaskExecution underTest = TaskExecution.create(
                "test.task", ExecutionSource.SCHEDULED, "node-1", plannedAt, CREATED_AT
        );
        underTest.queue(CREATED_AT);

        // When
        final boolean actual = underTest.start(CREATED_AT.plusMillis(50));

        // Then
        assertThat(actual).isTrue();
        assertThat(underTest.getStatus()).isEqualTo(TaskExecutionStatus.RUNNING);
        assertThat(underTest.getStartDelayMillis()).isEqualTo(expected);
    }

    @Test
    void succeed_RunningExecution_SucceededWithDuration() {
        // Given
        TaskExecution underTest = runningExecution();
        final TaskExecutionStatus expected = TaskExecutionStatus.SUCCEEDED;

        // When
        underTest.succeed(CREATED_AT.plusMillis(175));
        final TaskExecutionStatus actual = underTest.getStatus();

        // Then
        assertThat(actual).isEqualTo(expected);
        assertThat(underTest.getDurationMillis()).isEqualTo(150L);
        assertThat(underTest.isTerminal()).isTrue();
    }

    @Test
    void requestCancellation_RunningExecution_CancelledAfterInvocationStops() {
        // Given
        TaskExecution underTest = runningExecution();

        // When
        final boolean actual = underTest.requestCancellation(CREATED_AT.plusMillis(50), false);
        underTest.finishCancellation(CREATED_AT.plusMillis(75));

        // Then
        assertThat(actual).isTrue();
        assertThat(underTest.getStatus()).isEqualTo(TaskExecutionStatus.CANCELLED);
        assertThat(underTest.getStatusReason()).isEqualTo("CANCELLED_BY_OPERATOR");
    }

    @Test
    void skip_QueuedExecution_SkippedWithoutStart() {
        // Given
        TaskExecution underTest = TaskExecution.create(
                "test.task", ExecutionSource.MANUAL_ASYNC, "node-1", null, CREATED_AT
        );
        underTest.queue(CREATED_AT);

        // When
        final boolean actual = underTest.skip(CREATED_AT.plusMillis(1), "QUEUE_REJECTED");

        // Then
        assertThat(actual).isTrue();
        assertThat(underTest.getStatus()).isEqualTo(TaskExecutionStatus.SKIPPED);
        assertThat(underTest.getStatusReason()).isEqualTo("QUEUE_REJECTED");
        assertThat(underTest.getStartedAt()).isNull();
    }

    @Test
    void retry_AutomaticRetry_LineageContinued() {
        // Given
        TaskExecution parent = TaskExecution.create(
                "test.task", ExecutionSource.SCHEDULED, "node-1", CREATED_AT, CREATED_AT
        );

        // When
        final TaskExecution actual = TaskExecution.retry(
                parent, "node-1", CREATED_AT.plusSeconds(1), CREATED_AT, RetryTrigger.AUTOMATIC
        );

        // Then
        assertThat(actual.getParentExecutionId()).isEqualTo(parent.getExecutionId());
        assertThat(actual.getRootExecutionId()).isEqualTo(parent.getExecutionId());
        assertThat(actual.getRetrySeriesId()).isEqualTo(parent.getExecutionId());
        assertThat(actual.getAttempt()).isEqualTo(2);
        assertThat(actual.getRetryTrigger()).isEqualTo(RetryTrigger.AUTOMATIC);
    }

    @Test
    void retry_ManualRetry_NewSeriesCreated() {
        // Given
        TaskExecution parent = TaskExecution.create(
                "test.task", ExecutionSource.SCHEDULED, "node-1", CREATED_AT, CREATED_AT
        );

        // When
        final TaskExecution actual = TaskExecution.retry(
                parent, "node-1", CREATED_AT, CREATED_AT, RetryTrigger.MANUAL
        );

        // Then
        assertThat(actual.getParentExecutionId()).isEqualTo(parent.getExecutionId());
        assertThat(actual.getRootExecutionId()).isEqualTo(parent.getExecutionId());
        assertThat(actual.getRetrySeriesId()).isEqualTo(actual.getExecutionId());
        assertThat(actual.getAttempt()).isEqualTo(1);
        assertThat(actual.getRetryTrigger()).isEqualTo(RetryTrigger.MANUAL);
    }

    private TaskExecution runningExecution() {
        TaskExecution execution = TaskExecution.create(
                "test.task", ExecutionSource.MANUAL_ASYNC, "node-1", null, CREATED_AT
        );
        execution.queue(CREATED_AT);
        execution.start(CREATED_AT.plusMillis(25));
        return execution;
    }
}
