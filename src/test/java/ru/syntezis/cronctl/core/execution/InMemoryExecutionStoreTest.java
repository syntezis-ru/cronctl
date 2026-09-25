package ru.syntezis.cronctl.core.execution;

import org.junit.jupiter.api.Test;
import ru.syntezis.cronctl.domain.execution.ExecutionPage;
import ru.syntezis.cronctl.domain.execution.ExecutionQuery;
import ru.syntezis.cronctl.domain.execution.ScheduledTaskHealth;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.enums.ExecutionSource;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;
import ru.syntezis.cronctl.properties.CronctlProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryExecutionStoreTest {

    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");

    private final CronctlProperties.History history = new CronctlProperties.History();
    private final InMemoryExecutionStore underTest = new InMemoryExecutionStore(history);

    @Test
    void save_NewExecution_ExecutionRetrievableById() {
        // Given
        TaskExecution execution = execution("test.task", ExecutionSource.MANUAL_ASYNC, NOW);

        // When
        underTest.save(execution);
        final Optional<TaskExecution> actual = underTest.findById(execution.getExecutionId());

        // Then
        assertThat(actual).contains(execution);
    }

    @Test
    void findAll_FilterAndPagination_MatchingPageReturned() {
        // Given
        underTest.save(execution("task-a", ExecutionSource.SCHEDULED, NOW.minusSeconds(2)));
        underTest.save(execution("task-a", ExecutionSource.MANUAL_SYNC, NOW.minusSeconds(1)));
        underTest.save(execution("task-b", ExecutionSource.SCHEDULED, NOW));
        ExecutionQuery query = ExecutionQuery.builder()
                .source(ExecutionSource.SCHEDULED)
                .page(0)
                .size(1)
                .build();

        // When
        final ExecutionPage actual = underTest.findAll(query);

        // Then
        assertThat(actual.getTotal()).isEqualTo(2);
        assertThat(actual.getExecutions()).singleElement()
                .extracting(TaskExecution::getTaskKey)
                .isEqualTo("task-b");
        assertThat(actual.hasNext()).isTrue();
    }

    @Test
    void save_ExceededMaximum_OldestTerminalExecutionRemoved() {
        // Given
        history.setMaxEntries(1);
        TaskExecution oldest = succeeded("task-a", NOW.minusSeconds(2));
        TaskExecution newest = succeeded("task-a", NOW.minusSeconds(1));
        underTest.save(oldest);

        // When
        underTest.save(newest);

        // Then
        assertThat(underTest.findById(oldest.getExecutionId())).isEmpty();
        assertThat(underTest.findById(newest.getExecutionId())).contains(newest);
    }

    @Test
    void save_ActiveExecutionsExceedMaximum_TerminalCapacityUnaffected() {
        // Given
        history.setMaxEntries(1);
        TaskExecution active = execution("active-task", ExecutionSource.MANUAL_ASYNC, NOW);
        TaskExecution terminal = succeeded("scheduled-task", NOW.minusSeconds(1));
        underTest.save(active);

        // When
        underTest.save(terminal);
        final Optional<TaskExecution> actual = underTest.findById(terminal.getExecutionId());

        // Then
        assertThat(actual).contains(terminal);
        assertThat(underTest.findById(active.getExecutionId())).contains(active);
    }

    @Test
    void deleteExpired_ExpiredTerminalExecution_ExecutionRemoved() {
        // Given
        history.setRetention(Duration.ofHours(1));
        TaskExecution execution = succeeded("task-a", NOW.minus(Duration.ofHours(2)));
        underTest.save(execution);

        // When
        underTest.deleteExpired(NOW.minus(Duration.ofHours(1)));

        // Then
        assertThat(underTest.findById(execution.getExecutionId())).isEmpty();
    }

    @Test
    void save_ExpiredTerminalBeforeCleanup_ExecutionRetained() {
        // Given
        TaskExecution execution = succeeded("task-a", NOW.minus(Duration.ofDays(10)));

        // When
        underTest.save(execution);
        final Optional<TaskExecution> actual = underTest.findById(execution.getExecutionId());

        // Then
        assertThat(actual).contains(execution);
    }

    @Test
    void deleteExpired_ExpiredActiveExecution_ExecutionRetained() {
        // Given
        TaskExecution execution = execution("task-a", ExecutionSource.SCHEDULED, NOW.minus(Duration.ofDays(10)));
        underTest.save(execution);

        // When
        underTest.deleteExpired(NOW.minus(Duration.ofDays(1)));
        final Optional<TaskExecution> actual = underTest.findById(execution.getExecutionId());

        // Then
        assertThat(actual).contains(execution);
    }

    @Test
    void deleteExpired_ScheduledExecutionRemoved_HealthRetained() {
        // Given
        TaskExecution execution = succeeded("task-a", NOW.minus(Duration.ofDays(10)));
        underTest.save(execution);

        // When
        underTest.deleteExpired(NOW.minus(Duration.ofDays(1)));
        final ScheduledTaskHealth actual = underTest.getScheduledHealth("task-a");

        // Then
        assertThat(actual.getLastExecutionStatus()).isEqualTo(TaskExecutionStatus.SUCCEEDED);
        assertThat(actual.getLastSuccessAt()).isEqualTo(execution.getStartedAt());
    }

    @Test
    void save_ScheduledFailuresAndSuccess_HealthUpdated() {
        // Given
        TaskExecution firstFailure = failed("task-a", NOW.minusSeconds(3));
        TaskExecution secondFailure = failed("task-a", NOW.minusSeconds(2));
        TaskExecution success = succeeded("task-a", NOW.minusSeconds(1));
        underTest.save(firstFailure);
        underTest.save(secondFailure);

        // When
        underTest.save(success);
        final ScheduledTaskHealth actual = underTest.getScheduledHealth("task-a");

        // Then
        assertThat(actual.getLastExecutionStatus()).isEqualTo(TaskExecutionStatus.SUCCEEDED);
        assertThat(actual.getLastSuccessAt()).isEqualTo(success.getStartedAt());
        assertThat(actual.getConsecutiveFailures()).isZero();
    }

    private TaskExecution execution(String taskKey, ExecutionSource source, Instant createdAt) {
        return TaskExecution.create(taskKey, source, "node-1", null, createdAt);
    }

    private TaskExecution succeeded(String taskKey, Instant createdAt) {
        TaskExecution execution = execution(taskKey, ExecutionSource.SCHEDULED, createdAt);
        execution.queue(createdAt);
        execution.start(createdAt);
        execution.succeed(createdAt.plusMillis(10));
        return execution;
    }

    private TaskExecution failed(String taskKey, Instant createdAt) {
        TaskExecution execution = execution(taskKey, ExecutionSource.SCHEDULED, createdAt);
        execution.queue(createdAt);
        execution.start(createdAt);
        execution.fail(createdAt.plusMillis(10), new IllegalStateException(UUID.randomUUID().toString()));
        return execution;
    }

}
