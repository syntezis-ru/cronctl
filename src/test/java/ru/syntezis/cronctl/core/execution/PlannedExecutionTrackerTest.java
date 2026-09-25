package ru.syntezis.cronctl.core.execution;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import ru.syntezis.cronctl.core.ScheduledTaskNextExecutionResolver;
import ru.syntezis.cronctl.core.TaskRegistry;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.scheduled.ScheduleDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.ExecutionSource;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class PlannedExecutionTrackerTest {

    private static final Instant PLANNED_AT = Instant.parse("2026-07-20T10:00:00Z");

    private final PlannedExecutionTracker underTest = new PlannedExecutionTracker(
            new TaskRegistry(),
            new StaticListableBeanFactory().getBeanProvider(ScheduledTaskHolder.class),
            new ScheduledTaskNextExecutionResolver(Clock.systemUTC())
    );

    @Test
    void executionCompleted_FixedRateTask_NextSlotBasedOnPreviousPlan() {
        // Given
        final Instant expected = PLANNED_AT.plusSeconds(10);
        Task task = task(schedule(10L, -1L));
        TaskExecution execution = completedExecution(task.getTaskKey(), PLANNED_AT, PLANNED_AT.plusSeconds(4));

        // When
        underTest.executionCompleted(task, execution);
        final Instant actual = underTest.getPlannedAt(task.getTaskKey());

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void executionCompleted_FixedDelayTask_NextSlotBasedOnFinish() {
        // Given
        Instant finishedAt = PLANNED_AT.plusSeconds(4);
        final Instant expected = finishedAt.plusSeconds(10);
        Task task = task(schedule(-1L, 10L));
        TaskExecution execution = completedExecution(task.getTaskKey(), PLANNED_AT, finishedAt);

        // When
        underTest.executionCompleted(task, execution);
        final Instant actual = underTest.getPlannedAt(task.getTaskKey());

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void executionCompleted_FirstFixedRateRun_SchedulerSnapshotPreserved() {
        // Given
        final Instant expected = PLANNED_AT.plusSeconds(10);
        Task task = task(schedule(10L, -1L));
        TaskExecution execution = completedExecution(task.getTaskKey(), null, PLANNED_AT.plusSeconds(4));
        underTest.setPlannedAt(task.getTaskKey(), expected);

        // When
        underTest.executionCompleted(task, execution);
        final Instant actual = underTest.getPlannedAt(task.getTaskKey());

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void executionStarted_FirstFixedRateRun_NextSlotBasedOnActualStart() {
        // Given
        final Instant expected = PLANNED_AT.plusSeconds(10);
        Task task = task(schedule(10L, -1L));
        TaskExecution execution = TaskExecution.create(
                task.getTaskKey(), ExecutionSource.SCHEDULED, "node-1", null, PLANNED_AT
        );
        execution.queue(PLANNED_AT);
        execution.start(PLANNED_AT);

        // When
        underTest.executionStarted(task, execution);
        final Instant actual = underTest.getPlannedAt(task.getTaskKey());

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    private Task task(ScheduleDetails schedule) {
        return Task.builder()
                .details(ScheduledMethodDetails.builder()
                        .taskKey("test.task")
                        .methodName("run")
                        .schedule(schedule)
                        .build())
                .build();
    }

    private ScheduleDetails schedule(long fixedRate, long fixedDelay) {
        return ScheduleDetails.builder()
                .cron("")
                .zone("")
                .fixedRate(fixedRate)
                .fixedRateString("")
                .fixedDelay(fixedDelay)
                .fixedDelayString("")
                .initialDelay(-1L)
                .initialDelayString("")
                .timeUnit(TimeUnit.SECONDS)
                .scheduler("")
                .build();
    }

    private TaskExecution completedExecution(String taskKey, @Nullable Instant plannedAt,
                                             Instant finishedAt) {
        TaskExecution execution = TaskExecution.create(
                taskKey, ExecutionSource.SCHEDULED, "node-1", plannedAt, PLANNED_AT
        );
        execution.queue(PLANNED_AT);
        execution.start(PLANNED_AT.plusMillis(50));
        execution.succeed(finishedAt);
        return execution;
    }

}
