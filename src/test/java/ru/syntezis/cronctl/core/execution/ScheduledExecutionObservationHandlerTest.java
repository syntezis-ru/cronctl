package ru.syntezis.cronctl.core.execution;

import io.micrometer.observation.Observation;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.syntezis.cronctl.core.TaskRegistry;
import ru.syntezis.cronctl.core.state.TaskStateStore;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.ExecutionSource;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledExecutionObservationHandlerTest {

    @Mock
    private TaskRegistry taskRegistry;

    @Mock
    private ExecutionLifecycleService lifecycleService;

    @Mock
    private PlannedExecutionTracker plannedExecutionTracker;

    @Mock
    private TaskStateStore taskStateStore;

    @InjectMocks
    private ScheduledExecutionObservationHandler underTest;

    @Test
    void onStop_ContextWithoutExecution_NoLifecycleInteraction() throws ReflectiveOperationException {
        // Given
        Method method = SampleScheduledBean.class.getDeclaredMethod("scheduledMethod");
        Observation.Context context = scheduledContext(new SampleScheduledBean(), method);

        // When
        final Throwable actual = catchThrowable(() -> underTest.onStop(context));

        // Then
        assertThat(actual).isNull();
        verifyNoInteractions(lifecycleService, plannedExecutionTracker);
    }

    @Test
    void onStart_PersistedPausedTask_InvocationSkippedBeforeExecution() throws ReflectiveOperationException {
        // Given
        String taskKey = "billing.reconciliation";
        Instant plannedAt = Instant.parse("2026-07-20T10:00:00Z");
        Method method = SampleScheduledBean.class.getDeclaredMethod("scheduledMethod");
        Observation.Context context = scheduledContext(new SampleScheduledBean(), method);
        Task task = mock(Task.class);
        when(taskRegistry.getByScheduledMethod(SampleScheduledBean.class, method)).thenReturn(Optional.of(task));
        when(task.getTaskKey()).thenReturn(taskKey);
        when(task.isTogglingEnabled()).thenReturn(true);
        when(taskStateStore.isPaused(taskKey)).thenReturn(true);
        when(plannedExecutionTracker.getPlannedAt(taskKey)).thenReturn(plannedAt);

        // When
        final ThrowingCallable actual = () -> underTest.onStart(context);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(PausedScheduledExecutionException.class)
                .hasMessageContaining(taskKey);
        verify(lifecycleService).skipScheduled(taskKey, plannedAt, "PAUSED");
        verify(lifecycleService, never()).startScheduled(task, plannedAt);
    }

    @Test
    void onStart_ConcurrencyLimitReached_InvocationSkippedBeforeExecution() throws ReflectiveOperationException {
        // Given
        String taskKey = "billing.reconciliation";
        Instant plannedAt = Instant.parse("2026-07-20T10:00:00Z");
        Method method = SampleScheduledBean.class.getDeclaredMethod("scheduledMethod");
        Observation.Context context = scheduledContext(new SampleScheduledBean(), method);
        Task task = mock(Task.class);
        TaskExecution execution = TaskExecution.create(
                taskKey, ExecutionSource.SCHEDULED, "node-1", plannedAt, plannedAt
        );
        execution.queue(plannedAt);
        execution.skip(plannedAt, "CONCURRENT_EXECUTION");
        when(taskRegistry.getByScheduledMethod(SampleScheduledBean.class, method)).thenReturn(Optional.of(task));
        when(task.getTaskKey()).thenReturn(taskKey);
        when(plannedExecutionTracker.getPlannedAt(taskKey)).thenReturn(plannedAt);
        when(lifecycleService.startScheduled(task, plannedAt)).thenReturn(execution);

        // When
        final ThrowingCallable actual = () -> underTest.onStart(context);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(ConcurrentScheduledExecutionException.class)
                .hasMessageContaining("CONCURRENT_EXECUTION");
        verify(plannedExecutionTracker, never()).executionStarted(task, execution);
        verify(plannedExecutionTracker).executionCompleted(task, execution);
    }

    @Test
    void supportsContext_GenericObservationContext_False() {
        // Given
        Observation.Context context = new Observation.Context();

        // When
        final boolean actual = underTest.supportsContext(context);

        // Then
        assertThat(actual).isFalse();
    }

    private Observation.Context scheduledContext(Object target, Method method) throws ReflectiveOperationException {
        Assumptions.assumeTrue(ScheduledExecutionObservationHandler.isSupported());
        Class<?> contextClass = Class.forName(
                "org.springframework.scheduling.support.ScheduledTaskObservationContext"
        );
        Object context = contextClass.getConstructor(Object.class, Method.class).newInstance(target, method);
        return (Observation.Context) context;
    }

    static class SampleScheduledBean {

        void scheduledMethod() {
        }

    }

}
