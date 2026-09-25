package ru.syntezis.cronctl.core.execution;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.syntezis.cronctl.properties.CronctlProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ExecutionHistoryCleanupTest {

    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");

    @Mock
    private ExecutionStore executionStore;

    @Mock
    private ScheduledExecutorService scheduler;

    private CronctlProperties.History history;
    private ExecutionHistoryCleanup underTest;

    @BeforeEach
    void setUp() {
        history = new CronctlProperties.History();
        underTest = new ExecutionHistoryCleanup(executionStore, history);
        underTest.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        underTest.setScheduler(scheduler);
    }

    @Test
    void cleanupNow_ConfiguredRetention_ExpirationThresholdPassedToStore() {
        // Given
        final Instant expected = NOW.minus(Duration.ofDays(7));

        // When
        underTest.cleanupNow();

        // Then
        verify(executionStore).deleteExpired(expected);
    }

    @Test
    void afterPropertiesSet_ValidConfiguration_ImmediateAndPeriodicCleanupConfigured() {
        // Given
        final long expected = Duration.ofMinutes(10).toMillis();

        // When
        underTest.afterPropertiesSet();

        // Then
        verify(executionStore).deleteExpired(NOW.minus(Duration.ofDays(7)));
        verify(scheduler).scheduleWithFixedDelay(
                any(Runnable.class),
                eq(expected),
                eq(expected),
                eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void afterPropertiesSet_ZeroCleanupInterval_IllegalArgumentException() {
        // Given
        history.setCleanupInterval(Duration.ZERO);

        // When
        final ThrowingCallable actual = underTest::afterPropertiesSet;

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cronctl.history.cleanup-interval");
    }

    @Test
    void afterPropertiesSet_ZeroMaximumEntries_IllegalArgumentException() {
        // Given
        history.setMaxEntries(0);

        // When
        final ThrowingCallable actual = underTest::afterPropertiesSet;

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cronctl.history.max-entries");
    }

    @Test
    void afterPropertiesSet_NegativeRetention_IllegalArgumentException() {
        // Given
        history.setRetention(Duration.ofDays(-1));

        // When
        final ThrowingCallable actual = underTest::afterPropertiesSet;

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cronctl.history.retention");
    }

    @Test
    void afterPropertiesSet_StoreCleanupFails_PeriodicCleanupStillScheduled() {
        // Given
        doThrow(new IllegalStateException("Store unavailable"))
                .when(executionStore).deleteExpired(NOW.minus(Duration.ofDays(7)));

        // When
        final Throwable actual = catchThrowable(underTest::afterPropertiesSet);

        // Then
        assertThat(actual).isNull();
        verify(scheduler).scheduleWithFixedDelay(
                any(Runnable.class),
                eq(Duration.ofMinutes(10).toMillis()),
                eq(Duration.ofMinutes(10).toMillis()),
                eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    void shutdown_StartedCleanupScheduler_SchedulerStopped() {
        // Given
        underTest.afterPropertiesSet();

        // When
        underTest.shutdown();

        // Then
        verify(scheduler).shutdownNow();
    }

}
