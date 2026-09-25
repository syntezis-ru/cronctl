package ru.syntezis.cronctl.core.execution;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import ru.syntezis.cronctl.core.TaskRegistry;
import ru.syntezis.cronctl.core.state.TaskStateStore;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.lang.reflect.Method;

/**
 * Records executions initiated by Spring's native {@code @Scheduled} infrastructure.
 */
@RequiredArgsConstructor
public class ScheduledExecutionObservationHandler implements ObservationHandler<Observation.Context> {

    private static final String SCHEDULED_OBSERVATION_CONTEXT_CLASS_NAME =
            "org.springframework.scheduling.support.ScheduledTaskObservationContext";

    private static final String EXECUTION_CONTEXT_KEY = ScheduledExecutionObservationHandler.class.getName() + ".execution";
    private static final String TASK_CONTEXT_KEY = ScheduledExecutionObservationHandler.class.getName() + ".task";

    private final TaskRegistry taskRegistry;
    private final ExecutionLifecycleService lifecycleService;
    private final PlannedExecutionTracker plannedExecutionTracker;
    private final TaskStateStore taskStateStore;

    @Override
    public void onStart(Observation.Context context) {
        Class<?> targetClass = getTargetClass(context);
        Method method = getScheduledMethod(context);
        if (targetClass == null || method == null) {
            return;
        }
        taskRegistry.getByScheduledMethod(targetClass, method)
                .ifPresent(task -> startExecution(context, task));
    }

    @Override
    public void onStop(Observation.Context context) {
        TaskExecution execution = context.get(EXECUTION_CONTEXT_KEY);
        Task task = context.get(TASK_CONTEXT_KEY);
        if (execution == null || task == null) {
            return;
        }

        lifecycleService.completeScheduled(task, execution, context.getError());
        plannedExecutionTracker.executionCompleted(task, execution);
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return SCHEDULED_OBSERVATION_CONTEXT_CLASS_NAME.equals(context.getClass().getName());
    }

    /** Returns whether native Spring scheduled observations are available at runtime. */
    public static boolean isSupported() {
        return ClassUtils.isPresent(
                SCHEDULED_OBSERVATION_CONTEXT_CLASS_NAME,
                ScheduledExecutionObservationHandler.class.getClassLoader()
        );
    }

    private void startExecution(Observation.Context context, Task task) {
        if (task.isTogglingEnabled() && taskStateStore.isPaused(task.getTaskKey())) {
            lifecycleService.skipScheduled(
                    task.getTaskKey(), plannedExecutionTracker.getPlannedAt(task.getTaskKey()), "PAUSED"
            );
            throw new PausedScheduledExecutionException(task.getTaskKey());
        }

        TaskExecution execution = lifecycleService.startScheduled(
                task, plannedExecutionTracker.getPlannedAt(task.getTaskKey())
        );
        if (execution.getStatus() != TaskExecutionStatus.RUNNING) {
            plannedExecutionTracker.executionCompleted(task, execution);
            throw new ConcurrentScheduledExecutionException(
                    task.getTaskKey(), execution.getStatusReason() == null
                            ? execution.getStatus().name()
                            : execution.getStatusReason()
            );
        }
        plannedExecutionTracker.executionStarted(task, execution);
        context.put(EXECUTION_CONTEXT_KEY, execution);
        context.put(TASK_CONTEXT_KEY, task);
    }

    private @Nullable Class<?> getTargetClass(Observation.Context context) {
        Object value = invokeContextMethod(context, "getTargetClass");
        return value instanceof Class<?> targetClass ? targetClass : null;
    }

    private @Nullable Method getScheduledMethod(Observation.Context context) {
        Object value = invokeContextMethod(context, "getMethod");
        return value instanceof Method method ? method : null;
    }

    private @Nullable Object invokeContextMethod(Observation.Context context, String methodName) {
        Method method = ReflectionUtils.findMethod(context.getClass(), methodName);
        return method == null ? null : ReflectionUtils.invokeMethod(method, context);
    }
}
