package ru.syntezis.cronctl.core;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.config.DelayedTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.TaskSchedulerRouter;
import org.springframework.scheduling.config.TriggerTask;
import org.springframework.util.ClassUtils;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.exception.DisabledTogglingViolationException;
import ru.syntezis.cronctl.exception.ScheduledTaskHolderNotAvailableException;
import ru.syntezis.cronctl.exception.StateTogglerException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private final TaskSchedulerRouter taskSchedulerRouter;

    private final Map<UUID, List<org.springframework.scheduling.config.Task>> scheduleDefinitions = new ConcurrentHashMap<>();
    private final Map<UUID, List<ScheduledFuture<?>>> resumedSchedules = new ConcurrentHashMap<>();
    private final Map<UUID, Lock> taskLocks = new ConcurrentHashMap<>();

    /**
     * Resumes all Spring schedules previously paused for the given task.
     *
     * @param task task to enable
     * @return the enabled task
     */
    public Task enable(Task task) {
        validateTogglingEnabled(task);
        Lock taskLock = getTaskLock(task.getId());
        taskLock.lock();

        try {
            if (task.isEnabled()) {
                log.info("Task {} is already enabled", task.getId());
                return task;
            }

            List<org.springframework.scheduling.config.Task> definitions = scheduleDefinitions.get(task.getId());
            if (definitions == null || definitions.isEmpty()) {
                log.error("No saved schedule definition found for task {}", task.getId());
                throw new StateTogglerException("No saved schedule definition found for task " + task.getId());
            }

            List<ScheduledFuture<?>> scheduledFutures = new ArrayList<>(definitions.size());
            try {
                for (org.springframework.scheduling.config.Task definition : definitions) {
                    scheduledFutures.add(schedule(definition));
                }
            } catch (RuntimeException e) {
                scheduledFutures.forEach(future -> future.cancel(false));
                log.error("Failed to resume task {}", task.getId(), e);
                throw new StateTogglerException("Failed to resume task " + task.getId(), e);
            }

            resumedSchedules.put(task.getId(), List.copyOf(scheduledFutures));
            task.setEnabled(true);
            log.info("Task {} has been enabled", task.getId());
            return task;
        } finally {
            taskLock.unlock();
        }
    }

    /**
     * Pauses all Spring schedules associated with the given task.
     *
     * @param task task to disable
     * @param interruptIfRunning interrupt the task if it is currently running
     * @return the disabled task
     */
    public Task disable(Task task, boolean interruptIfRunning) {
        validateTogglingEnabled(task);
        Lock taskLock = getTaskLock(task.getId());
        taskLock.lock();

        try {
            if (!task.isEnabled()) {
                log.info("Task {} is already disabled", task.getId());
                return task;
            }

            List<ScheduledFuture<?>> managedFutures = resumedSchedules.remove(task.getId());
            try {
                if (managedFutures != null) {
                    managedFutures.forEach(future -> future.cancel(interruptIfRunning));
                } else {
                    List<ScheduledTask> scheduledTasks = findScheduledTasks(task);
                    scheduleDefinitions.putIfAbsent(task.getId(), scheduledTasks.stream()
                            .map(ScheduledTask::getTask)
                            .toList()
                    );
                    scheduledTasks.forEach(scheduledTask -> scheduledTask.cancel(interruptIfRunning));
                }
            } catch (StateTogglerException e) {
                throw e;
            } catch (RuntimeException e) {
                log.error("Failed to pause task {}", task.getId(), e);
                throw new StateTogglerException("Failed to pause task " + task.getId(), e);
            }

            task.setEnabled(false);
            log.info("Task {} has been disabled", task.getId());
            return task;
        } finally {
            taskLock.unlock();
        }
    }

    /**
     * Returns the next execution of a schedule created by {@link #enable(Task)}.
     *
     * @param taskId registered task id
     * @return next execution instant, or empty if the task has not been resumed or has no future execution
     */
    public Optional<Instant> findManagedNextExecutionAt(UUID taskId) {
        List<ScheduledFuture<?>> futures = resumedSchedules.get(taskId);
        if (futures == null) {
            return Optional.empty();
        }

        Instant now = Instant.now(taskSchedulerRouter.getClock());
        return futures.stream()
                .filter(future -> !future.isCancelled())
                .map(future -> future.getDelay(TimeUnit.MILLISECONDS))
                .filter(delay -> delay > 0)
                .map(now::plusMillis)
                .min(Comparator.naturalOrder());
    }

    private void validateTogglingEnabled(Task task) {
        if (!task.isTogglingEnabled()) {
            log.warn("Task {} is not toggling enabled", task.getId());
            throw new DisabledTogglingViolationException("Task is not toggling enabled");
        }
    }

    private Lock getTaskLock(UUID taskId) {
        return taskLocks.computeIfAbsent(taskId, ignoredTaskId -> new ReentrantLock());
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
            log.error("Schedule for task {} not found in ScheduledTaskHolder", task.getId());
            throw new StateTogglerException("Schedule for task " + task.getId() + " not found in ScheduledTaskHolder");
        }
        return scheduledTasks;
    }

    private ScheduledFuture<?> schedule(org.springframework.scheduling.config.Task definition) {
        ScheduledFuture<?> future;
        if (definition instanceof FixedRateTask fixedRateTask) {
            future = scheduleAtFixedRate(fixedRateTask);
        } else if (definition instanceof FixedDelayTask fixedDelayTask) {
            future = scheduleWithFixedDelay(fixedDelayTask);
        } else if (definition instanceof TriggerTask triggerTask) {
            future = taskSchedulerRouter.schedule(triggerTask.getRunnable(), triggerTask.getTrigger());
        } else if (definition instanceof DelayedTask delayedTask) {
            Instant startTime = Instant.now(taskSchedulerRouter.getClock()).plus(delayedTask.getInitialDelayDuration());
            future = taskSchedulerRouter.schedule(delayedTask.getRunnable(), startTime);
        } else {
            throw new StateTogglerException("Unsupported schedule type: " + definition.getClass().getName());
        }

        if (future == null) {
            throw new StateTogglerException("TaskScheduler did not create a scheduled future");
        }
        return future;
    }

    private ScheduledFuture<?> scheduleAtFixedRate(FixedRateTask task) {
        Duration initialDelay = task.getInitialDelayDuration();
        if (initialDelay.isZero()) {
            return taskSchedulerRouter.scheduleAtFixedRate(task.getRunnable(), task.getIntervalDuration());
        }
        Instant startTime = Instant.now(taskSchedulerRouter.getClock()).plus(initialDelay);
        return taskSchedulerRouter.scheduleAtFixedRate(task.getRunnable(), startTime, task.getIntervalDuration());
    }

    private ScheduledFuture<?> scheduleWithFixedDelay(FixedDelayTask task) {
        Duration initialDelay = task.getInitialDelayDuration();
        if (initialDelay.isZero()) {
            return taskSchedulerRouter.scheduleWithFixedDelay(task.getRunnable(), task.getIntervalDuration());
        }
        Instant startTime = Instant.now(taskSchedulerRouter.getClock()).plus(initialDelay);
        return taskSchedulerRouter.scheduleWithFixedDelay(task.getRunnable(), startTime, task.getIntervalDuration());
    }

    private boolean matches(ScheduledMethodReference reference, ScheduledTask scheduledTask) {
        String expectedDescription = ClassUtils.getQualifiedMethodName(reference.getMethod());
        return expectedDescription.equals(scheduledTask.getTask().getRunnable().toString());
    }

    /** Cancels schedules created by cronctl and releases the internal scheduler router. */
    @PreDestroy
    public void shutdown() {
        resumedSchedules.values().stream()
                .flatMap(List::stream)
                .forEach(future -> future.cancel(false));
        resumedSchedules.clear();
        taskSchedulerRouter.destroy();
    }
}
