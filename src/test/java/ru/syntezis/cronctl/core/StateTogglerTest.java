package ru.syntezis.cronctl.core;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.config.DelayedTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.TaskSchedulerRouter;
import org.springframework.scheduling.config.TriggerTask;
import org.springframework.util.ClassUtils;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.exception.DisabledTogglingViolationException;
import ru.syntezis.cronctl.exception.ScheduledTaskHolderNotAvailableException;
import ru.syntezis.cronctl.exception.StateTogglerException;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StateTogglerTest {

    @Mock
    private ObjectProvider<ScheduledTaskHolder> scheduledTaskHolderProvider;

    @Mock
    private ScheduledTaskHolder scheduledTaskHolder;

    @Mock
    private ScheduledTask scheduledTask;

    @Mock
    private TaskSchedulerRouter taskSchedulerRouter;

    @Mock
    private ScheduledFuture<Object> scheduledFuture;

    @Mock
    private Runnable scheduledRunnable;

    @Mock
    private Trigger trigger;

    @InjectMocks
    private StateToggler underTest;

    @Test
    void enable_EnabledTask_TaskReturnedWithoutChanges() throws NoSuchMethodException {
        // Given
        final boolean expected = true;
        Task task = buildTask(true, true);

        // When
        final Task actual = underTest.enable(task);

        // Then
        assertThat(actual)
                .isSameAs(task);
        assertThat(actual.isEnabled())
                .isEqualTo(expected);
        verifyNoInteractions(taskSchedulerRouter);
    }

    @Test
    void enable_TogglingDisabled_DisabledTogglingViolationException() throws NoSuchMethodException {
        // Given
        Task task = buildTask(false, false);

        // When
        final ThrowingCallable actual = () -> underTest.enable(task);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(DisabledTogglingViolationException.class)
                .hasMessage("Task is not toggling enabled");
        verifyNoInteractions(taskSchedulerRouter);
    }

    @Test
    void enable_DisabledTaskWithoutSavedSchedule_StateTogglerException() throws NoSuchMethodException {
        // Given
        Task task = buildTask(false, true);

        // When
        final ThrowingCallable actual = () -> underTest.enable(task);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(StateTogglerException.class)
                .hasMessageContaining("No saved schedule definition found");
        assertThat(task.isEnabled())
                .isFalse();
    }

    @Test
    void disable_TogglingDisabled_DisabledTogglingViolationException() throws NoSuchMethodException {
        // Given
        Task task = buildTask(true, false);

        // When
        final ThrowingCallable actual = () -> underTest.disable(task, true);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(DisabledTogglingViolationException.class)
                .hasMessage("Task is not toggling enabled");
        verifyNoInteractions(scheduledTaskHolderProvider);
    }

    @Test
    void disable_NoScheduledTaskHolders_ScheduledTaskHolderNotAvailableException() throws NoSuchMethodException {
        // Given
        Task task = buildTask(true, true);
        when(scheduledTaskHolderProvider.orderedStream()).thenReturn(Stream.empty());

        // When
        final ThrowingCallable actual = () -> underTest.disable(task, true);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(ScheduledTaskHolderNotAvailableException.class)
                .hasMessage("ScheduledTaskHolder is not available");
        assertThat(task.isEnabled())
                .isTrue();
    }

    @Test
    void disable_ScheduleNotFound_StateTogglerException() throws NoSuchMethodException {
        // Given
        Task task = buildTask(true, true);
        FixedRateTask definition = fixedRateTask(Duration.ofSeconds(10), Duration.ZERO);
        configureScheduledTask(definition, "another.Bean.scheduledMethod");

        // When
        final ThrowingCallable actual = () -> underTest.disable(task, true);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(StateTogglerException.class)
                .hasMessageContaining("not found in ScheduledTaskHolder");
        verify(scheduledTask, never()).cancel(anyBoolean());
        assertThat(task.isEnabled())
                .isTrue();
    }

    @Test
    void disable_AlreadyDisabledTask_TaskReturnedWithoutCancellation() throws NoSuchMethodException {
        // Given
        final boolean expected = false;
        Task task = buildTask(false, true);

        // When
        final Task actual = underTest.disable(task, true);

        // Then
        assertThat(actual)
                .isSameAs(task);
        assertThat(actual.isEnabled())
                .isEqualTo(expected);
        verifyNoInteractions(scheduledTaskHolderProvider, taskSchedulerRouter);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disable_EnabledTask_TaskDisabled(boolean interruptIfRunning) throws NoSuchMethodException {
        // Given
        final boolean expected = false;
        Task task = buildTask(true, true);
        FixedRateTask definition = fixedRateTask(Duration.ofSeconds(10), Duration.ZERO);
        configureMatchingScheduledTask(task, definition);

        // When
        final Task actual = underTest.disable(task, interruptIfRunning);

        // Then
        assertThat(actual)
                .isSameAs(task);
        assertThat(actual.isEnabled())
                .isEqualTo(expected);
        verify(scheduledTask).cancel(interruptIfRunning);
    }

    @Test
    void disable_ScheduledTaskCancellationFails_StateTogglerException() throws NoSuchMethodException {
        // Given
        final IllegalStateException expected = new IllegalStateException("Cancellation failed");
        Task task = buildTask(true, true);
        FixedRateTask definition = fixedRateTask(Duration.ofSeconds(10), Duration.ZERO);
        configureMatchingScheduledTask(task, definition);
        doThrow(expected).when(scheduledTask).cancel(true);

        // When
        final ThrowingCallable actual = () -> underTest.disable(task, true);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(StateTogglerException.class)
                .hasCause(expected);
        assertThat(task.isEnabled())
                .isTrue();
    }

    @Test
    void enable_PreviouslyDisabledFixedRateTask_TaskRescheduled() throws NoSuchMethodException {
        // Given
        final boolean expected = true;
        Duration interval = Duration.ofSeconds(10);
        Task task = buildTask(true, true);
        FixedRateTask definition = fixedRateTask(interval, Duration.ZERO);
        disableTask(task, definition);
        doReturn(scheduledFuture).when(taskSchedulerRouter)
                .scheduleAtFixedRate(definition.getRunnable(), interval);

        // When
        final Task actual = underTest.enable(task);

        // Then
        assertThat(actual.isEnabled())
                .isEqualTo(expected);
        verify(taskSchedulerRouter).scheduleAtFixedRate(definition.getRunnable(), interval);
    }

    @Test
    void enable_FixedRateTaskWithInitialDelay_TaskRescheduledAtStartTime() throws NoSuchMethodException {
        // Given
        Instant now = Instant.parse("2026-07-16T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        Duration interval = Duration.ofSeconds(10);
        Duration initialDelay = Duration.ofSeconds(3);
        final Instant expected = now.plus(initialDelay);
        Task task = buildTask(true, true);
        FixedRateTask definition = fixedRateTask(interval, initialDelay);
        disableTask(task, definition);
        when(taskSchedulerRouter.getClock()).thenReturn(clock);
        doReturn(scheduledFuture).when(taskSchedulerRouter)
                .scheduleAtFixedRate(definition.getRunnable(), expected, interval);

        // When
        underTest.enable(task);

        // Then
        verify(taskSchedulerRouter).scheduleAtFixedRate(definition.getRunnable(), expected, interval);
        assertThat(task.isEnabled())
                .isTrue();
    }

    @Test
    void enable_PreviouslyDisabledFixedDelayTask_TaskRescheduled() throws NoSuchMethodException {
        // Given
        Duration interval = Duration.ofSeconds(10);
        Task task = buildTask(true, true);
        FixedDelayTask definition = new FixedDelayTask(scheduledRunnable, interval, Duration.ZERO);
        disableTask(task, definition);
        doReturn(scheduledFuture).when(taskSchedulerRouter)
                .scheduleWithFixedDelay(definition.getRunnable(), interval);

        // When
        underTest.enable(task);

        // Then
        verify(taskSchedulerRouter).scheduleWithFixedDelay(definition.getRunnable(), interval);
        assertThat(task.isEnabled())
                .isTrue();
    }

    @Test
    void enable_PreviouslyDisabledTriggerTask_TaskRescheduled() throws NoSuchMethodException {
        // Given
        Task task = buildTask(true, true);
        TriggerTask definition = new TriggerTask(scheduledRunnable, trigger);
        disableTask(task, definition);
        doReturn(scheduledFuture).when(taskSchedulerRouter)
                .schedule(definition.getRunnable(), trigger);

        // When
        underTest.enable(task);

        // Then
        verify(taskSchedulerRouter).schedule(definition.getRunnable(), trigger);
        assertThat(task.isEnabled())
                .isTrue();
    }

    @Test
    void enable_PreviouslyDisabledOneTimeTask_TaskRescheduled() throws NoSuchMethodException {
        // Given
        Instant now = Instant.parse("2026-07-16T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        Duration initialDelay = Duration.ofSeconds(3);
        final Instant expected = now.plus(initialDelay);
        Task task = buildTask(true, true);
        DelayedTask definition = new DelayedTask(scheduledRunnable, initialDelay);
        disableTask(task, definition);
        when(taskSchedulerRouter.getClock()).thenReturn(clock);
        doReturn(scheduledFuture).when(taskSchedulerRouter)
                .schedule(definition.getRunnable(), expected);

        // When
        underTest.enable(task);

        // Then
        verify(taskSchedulerRouter).schedule(definition.getRunnable(), expected);
        assertThat(task.isEnabled())
                .isTrue();
    }

    @Test
    void enable_SecondScheduleFails_CreatedScheduleCancelled() throws NoSuchMethodException {
        // Given
        final IllegalStateException expected = new IllegalStateException("Scheduling failed");
        Task task = buildTask(true, true);
        Duration interval = Duration.ofSeconds(10);
        FixedRateTask fixedRateDefinition = fixedRateTask(interval, Duration.ZERO);
        FixedDelayTask fixedDelayDefinition = new FixedDelayTask(scheduledRunnable, interval, Duration.ZERO);
        ScheduledTask secondScheduledTask = mock(ScheduledTask.class);
        configureMatchingScheduledTasks(task, List.of(
                scheduledTaskWithDefinition(scheduledTask, fixedRateDefinition),
                scheduledTaskWithDefinition(secondScheduledTask, fixedDelayDefinition)
        ));
        underTest.disable(task, false);
        doReturn(scheduledFuture).when(taskSchedulerRouter)
                .scheduleAtFixedRate(fixedRateDefinition.getRunnable(), interval);
        when(taskSchedulerRouter.scheduleWithFixedDelay(fixedDelayDefinition.getRunnable(), interval))
                .thenThrow(expected);

        // When
        final ThrowingCallable actual = () -> underTest.enable(task);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(StateTogglerException.class)
                .hasCause(expected);
        verify(scheduledFuture).cancel(false);
        assertThat(task.isEnabled())
                .isFalse();
    }

    @Test
    void findManagedNextExecutionAt_ResumedTask_NextExecutionInstant() throws NoSuchMethodException {
        // Given
        Instant now = Instant.parse("2026-07-16T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        Duration interval = Duration.ofSeconds(10);
        final Optional<Instant> expected = Optional.of(now.plusSeconds(5));
        Task task = buildTask(true, true);
        FixedRateTask definition = fixedRateTask(interval, Duration.ZERO);
        disableTask(task, definition);
        doReturn(scheduledFuture).when(taskSchedulerRouter)
                .scheduleAtFixedRate(definition.getRunnable(), interval);
        underTest.enable(task);
        when(taskSchedulerRouter.getClock()).thenReturn(clock);
        when(scheduledFuture.isCancelled()).thenReturn(false);
        when(scheduledFuture.getDelay(TimeUnit.MILLISECONDS)).thenReturn(5_000L);

        // When
        final Optional<Instant> actual = underTest.findManagedNextExecutionAt(task.getId());

        // Then
        assertThat(actual)
                .isEqualTo(expected);
    }

    @Test
    void shutdown_ResumedTask_ScheduleCancelledAndRouterDestroyed() throws NoSuchMethodException {
        // Given
        Duration interval = Duration.ofSeconds(10);
        Task task = buildTask(true, true);
        FixedRateTask definition = fixedRateTask(interval, Duration.ZERO);
        disableTask(task, definition);
        doReturn(scheduledFuture).when(taskSchedulerRouter)
                .scheduleAtFixedRate(definition.getRunnable(), interval);
        underTest.enable(task);

        // When
        underTest.shutdown();

        // Then
        verify(scheduledFuture).cancel(false);
        verify(taskSchedulerRouter).destroy();
    }

    private Task buildTask(boolean enabled, boolean togglingEnabled) throws NoSuchMethodException {
        Method method = SampleScheduledBean.class.getDeclaredMethod("scheduledMethod");
        return Task.builder()
                .enabled(enabled)
                .togglingEnabled(togglingEnabled)
                .details(ScheduledMethodDetails.builder()
                        .id(UUID.randomUUID())
                        .methodName(method.getName())
                        .build()
                )
                .reference(ScheduledMethodReference.builder()
                        .beanName("sampleScheduledBean")
                        .bean(new SampleScheduledBean())
                        .method(method)
                        .build()
                )
                .build();
    }

    private FixedRateTask fixedRateTask(Duration interval, Duration initialDelay) {
        return new FixedRateTask(scheduledRunnable, interval, initialDelay);
    }

    private void disableTask(Task task, org.springframework.scheduling.config.Task definition) {
        configureMatchingScheduledTask(task, definition);
        underTest.disable(task, false);
    }

    private void configureMatchingScheduledTask(Task task, org.springframework.scheduling.config.Task definition) {
        String runnableDescription = ClassUtils.getQualifiedMethodName(task.getReference().getMethod());
        configureScheduledTask(definition, runnableDescription);
    }

    private void configureScheduledTask(org.springframework.scheduling.config.Task definition, String runnableDescription) {
        when(scheduledRunnable.toString()).thenReturn(runnableDescription);
        configureScheduledTasks(Set.of(scheduledTaskWithDefinition(scheduledTask, definition)));
    }

    private void configureMatchingScheduledTasks(Task task, List<ScheduledTask> scheduledTasks) {
        String runnableDescription = ClassUtils.getQualifiedMethodName(task.getReference().getMethod());
        when(scheduledRunnable.toString()).thenReturn(runnableDescription);
        configureScheduledTasks(new LinkedHashSet<>(scheduledTasks));
    }

    private ScheduledTask scheduledTaskWithDefinition(
            ScheduledTask targetScheduledTask, org.springframework.scheduling.config.Task definition) {
        when(targetScheduledTask.getTask()).thenReturn(definition);
        return targetScheduledTask;
    }

    private void configureScheduledTasks(Set<ScheduledTask> scheduledTasks) {
        when(scheduledTaskHolderProvider.orderedStream()).thenReturn(Stream.of(scheduledTaskHolder));
        when(scheduledTaskHolder.getScheduledTasks()).thenReturn(scheduledTasks);
    }

    private static class SampleScheduledBean {

        private void scheduledMethod() {
            // No-op.
        }
    }
}
