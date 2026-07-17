package ru.syntezis.cronctl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import ru.syntezis.cronctl.domain.scheduled.ScheduleDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.task.Task;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NextExecutionTimeResolverTest {

    @Mock
    private ObjectProvider<ScheduledTaskHolder> scheduledTaskHolderProvider;

    @Mock
    private StateToggler stateToggler;

    @InjectMocks
    private NextExecutionTimeResolver underTest;

    @Test
    void computeNextExecutionAt_DisabledTask_Null() {
        // Given
        Task task = buildTask(false, null);

        // When
        final Instant actual = underTest.computeNextExecutionAt(task);

        // Then
        assertThat(actual)
                .isNull();

        verifyNoInteractions(stateToggler, scheduledTaskHolderProvider);
    }

    @Test
    void computeNextExecutionAt_ManagedSchedule_ManagedExecutionInstant() {
        // Given
        final Instant expected = Instant.parse("2026-07-16T10:00:00Z");
        Task task = buildTask(true, null);
        when(stateToggler.findManagedNextExecutionAt(task.getId()))
                .thenReturn(Optional.of(expected));

        // When
        final Instant actual = underTest.computeNextExecutionAt(task);

        // Then
        assertThat(actual)
                .isEqualTo(expected);

        verifyNoInteractions(scheduledTaskHolderProvider);
    }

    @Test
    void computeNextExecutionAt_CronSchedule_CronExpressionTime() {
        // Given
        ZoneId zoneId = ZoneId.of("Europe/Moscow");
        LocalTime expected = LocalTime.of(9, 0);
        ScheduleDetails schedule = ScheduleDetails.builder()
                .cron("0 0 9 * * MON")
                .zone(zoneId.getId())
                .build();
        Task task = buildTask(true, schedule);

        // When
        Instant nextExecutionAt = underTest.computeNextExecutionAt(task);
        final LocalTime actual = nextExecutionAt.atZone(zoneId).toLocalTime();

        // Then
        assertThat(actual)
                .isEqualTo(expected);

        verifyNoInteractions(stateToggler, scheduledTaskHolderProvider);
    }

    private Task buildTask(boolean enabled, ScheduleDetails schedule) {
        return Task.builder()
                .enabled(enabled)
                .details(ScheduledMethodDetails.builder()
                        .id(UUID.randomUUID())
                        .schedule(schedule)
                        .build()
                )
                .build();
    }
}
