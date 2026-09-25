package ru.syntezis.cronctl.core;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.TriggerTask;
import org.springframework.util.ClassUtils;
import ru.syntezis.cronctl.core.execution.ExecutionLifecycleService;
import ru.syntezis.cronctl.core.execution.PlannedExecutionTracker;
import ru.syntezis.cronctl.core.state.TaskStateStore;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.exception.DisabledTogglingViolationException;
import ru.syntezis.cronctl.exception.ScheduledTaskHolderNotAvailableException;
import ru.syntezis.cronctl.exception.StateTogglerException;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Pauses and resumes the Spring schedules associated with registered cronctl tasks.
 */
@RequiredArgsConstructor
@Slf4j
public class StateToggler {

    private final ObjectProvider<ScheduledTaskHolder> scheduledTaskHolderProvider;
    private final CompatibleTaskScheduler compatibleTaskScheduler;
    private final ExecutionLifecycleService lifecycleService;
    private final PlannedExecutionTracker plannedExecutionTracker;
    private final TaskStateStore taskStateStore;

    private final Map<String, List<org.springframework.scheduling.config.Task>> scheduleDefinitions = new ConcurrentHashMap<>();
    private final Map<String, List<ScheduledFuture<?>>> resumedSchedules = new ConcurrentHashMap<>();
    private final Map<String, Lock> taskLocks = new ConcurrentHashMap<>();

    /**
     * Resumes all Spring schedules previously paused for the given task.
     *
     * @param task task to enable
     * @return the enabled task
     */
    public Task enable(Task task) {
        validateTogglingEnabled(task);
        Lock taskLock = getTaskLock(task.getTaskKey());
        taskLock.lock();

        try {
            if (task.isEnabled()) {
                taskStateStore.clearPaused(task.getTaskKey());
                log.info("Task {} is already enabled", task.getTaskKey());
                return task;
            }

            List<org.springframework.scheduling.config.Task> definitions = scheduleDefinitions.get(task.getTaskKey());
            if (definitions == null || definitions.isEmpty()) {
                log.error("No saved schedule definition found for task {}", task.getTaskKey());
                throw new StateTogglerException("No saved schedule definition found for task " + task.getTaskKey());
            }

            List<ScheduledFuture<?>> scheduledFutures = new ArrayList<>(definitions.size());
            try {
                for (org.springframework.scheduling.config.Task definition : definitions) {
                    scheduledFutures.add(schedule(definition));
                }
            } catch (RuntimeException e) {
                scheduledFutures.forEach(future -> future.cancel(false));
                log.error("Failed to resume task {}", task.getTaskKey(), e);
                throw new StateTogglerException("Failed to resume task " + task.getTaskKey(), e);
            }

            resumedSchedules.put(task.getTaskKey(), List.copyOf(scheduledFutures));
            task.setEnabled(true);
            plannedExecutionTracker.setPlannedAt(task.getTaskKey(), findManagedNextExecutionAt(task.getTaskKey())
                    .orElse(null));
            taskStateStore.clearPaused(task.getTaskKey());
            log.info("Task {} has been enabled", task.getTaskKey());
            return task;
        } finally {
            taskLock.unlock();
        }
    }

    /**
     * Pauses all Spring schedules associated with the given task.
     *
     * @param task task to disable
     * @param interrupt interrupt the task if it is currently running
     * @return the disabled task
     */
    public Task disable(Task task, boolean interrupt) {
        validateTogglingEnabled(task);
        Lock taskLock = getTaskLock(task.getTaskKey());
        taskLock.lock();

        try {
            if (!task.isEnabled()) {
                taskStateStore.markPaused(task.getTaskKey());
                log.info("Task {} is already disabled", task.getTaskKey());
                return task;
            }

            List<ScheduledFuture<?>> managedFutures = resumedSchedules.remove(task.getTaskKey());
            try {
                if (interrupt) {
                    lifecycleService.requestScheduledCancellation(task.getTaskKey());
                }
                if (managedFutures != null) {
                    managedFutures.forEach(future -> future.cancel(interrupt));
                } else {
                    List<ScheduledTask> scheduledTasks = findScheduledTasks(task);
                    scheduleDefinitions.putIfAbsent(task.getTaskKey(), scheduledTasks.stream()
                            .map(ScheduledTask::getTask)
                            .toList()
                    );
                    scheduledTasks.forEach(scheduledTask -> scheduledTask.cancel(interrupt));
                }
            } catch (StateTogglerException e) {
                throw e;
            } catch (RuntimeException e) {
                log.error("Failed to pause task {}", task.getTaskKey(), e);
                throw new StateTogglerException("Failed to pause task " + task.getTaskKey(), e);
            }

            task.setEnabled(false);
            plannedExecutionTracker.clear(task.getTaskKey());
            taskStateStore.markPaused(task.getTaskKey());
            log.info("Task {} has been disabled", task.getTaskKey());
            return task;
        } finally {
            taskLock.unlock();
        }
    }

    /**
     * Returns the next execution of a schedule created by {@link #enable(Task)}.
     *
     * @param taskKey registered task key
     * @return next execution instant, or empty if the task has not been resumed or has no future execution
     */
    public Optional<Instant> findManagedNextExecutionAt(String taskKey) {
        List<ScheduledFuture<?>> futures = resumedSchedules.get(taskKey);
        if (futures == null) {
            return Optional.empty();
        }

        Clock schedulerClock = compatibleTaskScheduler.getTaskScheduler().getClock();
        Instant now = Instant.now(schedulerClock);
        return futures.stream()
                .filter(future -> !future.isCancelled())
                .map(future -> future.getDelay(TimeUnit.MILLISECONDS))
                .filter(delay -> delay > 0)
                .map(now::plusMillis)
                .min(Comparator.naturalOrder());
    }

    private void validateTogglingEnabled(Task task) {
        if (!task.isTogglingEnabled()) {
            log.warn("Task {} is not toggling enabled", task.getTaskKey());
            throw new DisabledTogglingViolationException("Task is not toggling enabled");
        }
    }

    private Lock getTaskLock(String taskKey) {
        return taskLocks.computeIfAbsent(taskKey, ignoredTaskKey -> new ReentrantLock());
    }

    private List<ScheduledTask> findScheduledTasks(Task task) {
        List<ScheduledTaskHolder> holders = scheduledTaskHolderProvider.orderedStream().toList();
        if (holders.isEmpty()) {
            log.error("ScheduledTaskHolder is not available");
            throw new ScheduledTaskHolderNotAvailableException("ScheduledTaskHolder is not available");
        }

        List<ScheduledTask> scheduledTasks = holders.stream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .filter(scheduledTask -> matches(task.getReference(), scheduledTask))
                .toList();
        if (scheduledTasks.isEmpty()) {
            log.error("Schedule for task {} not found in ScheduledTaskHolder", task.getTaskKey());
            throw new StateTogglerException("Schedule for task " + task.getTaskKey() + " not found in ScheduledTaskHolder");
        }
        return scheduledTasks;
    }

    private ScheduledFuture<?> schedule(org.springframework.scheduling.config.Task definition) {
        TaskScheduler taskScheduler = compatibleTaskScheduler.getTaskScheduler();
        ScheduledFuture<?> future;
        if (definition instanceof FixedRateTask fixedRateTask) {
            future = scheduleAtFixedRate(taskScheduler, fixedRateTask);
        } else if (definition instanceof FixedDelayTask fixedDelayTask) {
            future = scheduleWithFixedDelay(taskScheduler, fixedDelayTask);
        } else if (definition instanceof TriggerTask triggerTask) {
            future = taskScheduler.schedule(triggerTask.getRunnable(), triggerTask.getTrigger());
        } else if (hasInitialDelayDuration(definition)) {
            Duration initialDelay = getInitialDelayDuration(definition);
            Instant startTime = Instant.now(taskScheduler.getClock()).plus(initialDelay);
            future = taskScheduler.schedule(definition.getRunnable(), startTime);
        } else {
            throw new StateTogglerException("Unsupported schedule type: " + definition.getClass().getName());
        }

        if (future == null) {
            throw new StateTogglerException("TaskScheduler did not create a scheduled future");
        }
        return future;
    }

    private ScheduledFuture<?> scheduleAtFixedRate(TaskScheduler taskScheduler, FixedRateTask task) {
        Duration initialDelay = task.getInitialDelayDuration();
        if (initialDelay.isZero()) {
            return taskScheduler.scheduleAtFixedRate(task.getRunnable(), task.getIntervalDuration());
        }
        Instant startTime = Instant.now(taskScheduler.getClock()).plus(initialDelay);
        return taskScheduler.scheduleAtFixedRate(task.getRunnable(), startTime, task.getIntervalDuration());
    }

    private ScheduledFuture<?> scheduleWithFixedDelay(TaskScheduler taskScheduler, FixedDelayTask task) {
        Duration initialDelay = task.getInitialDelayDuration();
        if (initialDelay.isZero()) {
            return taskScheduler.scheduleWithFixedDelay(task.getRunnable(), task.getIntervalDuration());
        }
        Instant startTime = Instant.now(taskScheduler.getClock()).plus(initialDelay);
        return taskScheduler.scheduleWithFixedDelay(task.getRunnable(), startTime, task.getIntervalDuration());
    }

    private boolean hasInitialDelayDuration(org.springframework.scheduling.config.Task definition) {
        return org.springframework.util.ReflectionUtils.findMethod(
                definition.getClass(), "getInitialDelayDuration"
        ) != null;
    }

    private Duration getInitialDelayDuration(org.springframework.scheduling.config.Task definition) {
        Method method = org.springframework.util.ReflectionUtils.findMethod(
                definition.getClass(), "getInitialDelayDuration"
        );
        if (method == null) {
            throw new StateTogglerException("Schedule does not expose an initial delay");
        }
        org.springframework.util.ReflectionUtils.makeAccessible(method);
        Object value = org.springframework.util.ReflectionUtils.invokeMethod(method, definition);
        if (value instanceof Duration duration) {
            return duration;
        }
        throw new StateTogglerException("Schedule initial delay is not a Duration");
    }

    private boolean matches(ScheduledMethodReference reference, ScheduledTask scheduledTask) {
        String expectedDescription = ClassUtils.getQualifiedMethodName(reference.getMethod());
        return expectedDescription.equals(scheduledTask.getTask().getRunnable().toString());
    }

    /** Cancels schedules created by cronctl. */
    @PreDestroy
    public void shutdown() {
        resumedSchedules.values().stream()
                .flatMap(List::stream)
                .forEach(future -> future.cancel(false));
        resumedSchedules.clear();
    }
}
