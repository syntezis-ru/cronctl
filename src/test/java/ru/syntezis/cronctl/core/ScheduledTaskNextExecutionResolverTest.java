package ru.syntezis.cronctl.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledTaskNextExecutionResolverTest {

    @Mock
    private ScheduledTask scheduledTask;

    @Mock
    private ScheduledFuture<Object> scheduledFuture;

    private ScheduledTaskNextExecutionResolver underTest;

    @Test
    void resolve_NextExecutionMethodAvailable_NextExecutionInstant() throws NoSuchMethodException {
        // Given
        final Instant expected = Instant.parse("2026-09-25T12:00:00Z");
        Method nextExecutionMethod = ScheduledTask.class.getMethod("nextExecution");
        underTest = new ScheduledTaskNextExecutionResolver(
                Clock.systemUTC(), nextExecutionMethod, futureField()
        );
        when(scheduledTask.nextExecution()).thenReturn(expected);

        // When
        final Instant actual = underTest.resolve(scheduledTask);

        // Then
        assertThat(actual)
                .isEqualTo(expected);
    }

    @Test
    void resolve_NextExecutionMethodFails_Null() throws NoSuchMethodException {
        // Given
        Method nextExecutionMethod = ScheduledTask.class.getMethod("nextExecution");
        underTest = new ScheduledTaskNextExecutionResolver(
                Clock.systemUTC(), nextExecutionMethod, futureField()
        );
        when(scheduledTask.nextExecution()).thenThrow(new IllegalStateException("Unavailable"));

        // When
        final Instant actual = underTest.resolve(scheduledTask);

        // Then
        assertThat(actual)
                .isNull();
    }

    @Test
    void resolve_LegacyScheduledFuture_NextExecutionInstant() {
        // Given
        final Instant expected = Instant.parse("2026-09-25T12:00:05Z");
        Clock clock = Clock.fixed(Instant.parse("2026-09-25T12:00:00Z"), ZoneOffset.UTC);
        Field futureField = futureField();
        underTest = new ScheduledTaskNextExecutionResolver(clock, null, futureField);
        ReflectionUtils.setField(futureField, scheduledTask, scheduledFuture);
        when(scheduledFuture.getDelay(TimeUnit.MILLISECONDS)).thenReturn(5_000L);

        // When
        final Instant actual = underTest.resolve(scheduledTask);

        // Then
        assertThat(actual)
                .isEqualTo(expected);
    }

    @Test
    void resolve_CancelledLegacyScheduledFuture_Null() {
        // Given
        Field futureField = futureField();
        underTest = new ScheduledTaskNextExecutionResolver(Clock.systemUTC(), null, futureField);
        ReflectionUtils.setField(futureField, scheduledTask, scheduledFuture);
        when(scheduledFuture.isCancelled()).thenReturn(true);

        // When
        final Instant actual = underTest.resolve(scheduledTask);

        // Then
        assertThat(actual)
                .isNull();
    }

    @Test
    void resolve_NegativeLegacyScheduledFutureDelay_Null() {
        // Given
        Field futureField = futureField();
        underTest = new ScheduledTaskNextExecutionResolver(Clock.systemUTC(), null, futureField);
        ReflectionUtils.setField(futureField, scheduledTask, scheduledFuture);
        when(scheduledFuture.getDelay(TimeUnit.MILLISECONDS)).thenReturn(-1L);

        // When
        final Instant actual = underTest.resolve(scheduledTask);

        // Then
        assertThat(actual)
                .isNull();
    }

    @Test
    void resolve_ResolverMethodsUnavailable_Null() {
        // Given
        underTest = new ScheduledTaskNextExecutionResolver(Clock.systemUTC(), null, null);

        // When
        final Instant actual = underTest.resolve(scheduledTask);

        // Then
        assertThat(actual)
                .isNull();
    }

    private Field futureField() {
        Field futureField = Objects.requireNonNull(ReflectionUtils.findField(ScheduledTask.class, "future"));
        ReflectionUtils.makeAccessible(futureField);
        return futureField;
    }
}
