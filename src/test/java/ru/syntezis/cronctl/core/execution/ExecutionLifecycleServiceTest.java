package ru.syntezis.cronctl.core.execution;

import org.junit.jupiter.api.Test;
import ru.syntezis.cronctl.core.sync.BlockingTaskExecutor;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.ConcurrencyPolicy;
import ru.syntezis.cronctl.enums.ExecutionSource;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;
import ru.syntezis.cronctl.properties.CronctlProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionLifecycleServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");

    private final ExecutionStore executionStore = new InMemoryExecutionStore(new CronctlProperties.History());
    private final ExecutionLifecycleService underTest = new ExecutionLifecycleService(
            executionStore, new BlockingTaskExecutor(), new TaskConcurrencyController(),
            new DisabledRetryLifecycleHandler(),
            Clock.fixed(NOW, ZoneOffset.UTC), "node-1"
    );

    @Test
    void skipScheduled_PausedTask_SkippedScheduledExecutionStored() {
        // Given
        String taskKey = "billing.reconciliation";
        Instant plannedAt = NOW.minusSeconds(2);

        // When
        final TaskExecution actual = underTest.skipScheduled(taskKey, plannedAt, "PAUSED");

        // Then
        assertThat(actual.getTaskKey()).isEqualTo(taskKey);
        assertThat(actual.getSource()).isEqualTo(ExecutionSource.SCHEDULED);
        assertThat(actual.getStatus()).isEqualTo(TaskExecutionStatus.SKIPPED);
        assertThat(actual.getStatusReason()).isEqualTo("PAUSED");
        assertThat(actual.getFinishedAt()).isEqualTo(NOW);
        assertThat(executionStore.findById(actual.getExecutionId())).contains(actual);
    }

    @Test
    void executeSynchronously_ScheduledExecutionAlreadyAtSkipLimit_ConcurrentExecutionSkipped() {
        // Given
        Task task = Task.builder()
                .concurrencyPolicy(ConcurrencyPolicy.SKIP)
                .maxConcurrentExecutions(1)
                .details(ScheduledMethodDetails.builder()
                        .taskKey("billing.reconciliation")
                        .build())
                .build();
        TaskExecution scheduledExecution = underTest.startScheduled(task, NOW);

        try {
            // When
            final TaskExecution actual = underTest.executeSynchronously(task);

            // Then
            assertThat(actual.getSource()).isEqualTo(ExecutionSource.MANUAL_SYNC);
            assertThat(actual.getStatus()).isEqualTo(TaskExecutionStatus.SKIPPED);
            assertThat(actual.getStatusReason()).isEqualTo("CONCURRENT_EXECUTION");
        } finally {
            underTest.completeScheduled(task, scheduledExecution, null);
        }
    }

}
