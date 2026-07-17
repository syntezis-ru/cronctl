package ru.syntezis.cronctl.core;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.util.ClassUtils;
import ru.syntezis.cronctl.domain.scheduled.ScheduleDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.util.ScheduleUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves the next scheduled execution time for a {@link Task}.
 *
 * <p>Cron schedules are calculated from their expression and time zone. This keeps calendar-based
 * execution times tied to the wall clock even when the JVM was suspended or the system clock changed.
 *
 * <p>Fixed-rate and fixed-delay schedules are resolved from their live scheduled future because
 * their next execution depends on runtime scheduler state.
 */
@RequiredArgsConstructor
public class NextExecutionTimeResolver {

    private final ObjectProvider<ScheduledTaskHolder> scheduledTaskHolderProvider;
    private final StateToggler stateToggler;

    /**
     * Returns the next execution time for the given task, or {@code null} if it cannot be determined.
     *
     * @param task the task to inspect
     * @return next execution instant, or {@code null} for non-cron tasks without a live future
     */
    public @Nullable Instant computeNextExecutionAt(Task task) {
        if (!task.isEnabled()) {
            return null;
        }

        ScheduleDetails schedule = task.getDetails().getSchedule();
        if (isCronSchedule(schedule)) {
            return ScheduleUtils.computeNextExecutionAt(schedule);
        }

        Optional<Instant> managedNextExecution = stateToggler.findManagedNextExecutionAt(task.getId());
        if (managedNextExecution.isPresent()) {
            return managedNextExecution.get();
        }

        List<ScheduledTask> matchingTasks = scheduledTaskHolderProvider.orderedStream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .filter(scheduledTask -> matches(task.getReference(), scheduledTask))
                .toList();

        if (matchingTasks.isEmpty()) {
            return null;
        }

        return matchingTasks.stream()
                .map(ScheduledTask::nextExecution)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private boolean isCronSchedule(@Nullable ScheduleDetails schedule) {
        return schedule != null && schedule.getCron() != null && !schedule.getCron().isBlank();
    }

    private boolean matches(ScheduledMethodReference reference, ScheduledTask scheduledTask) {
        String expectedDescription = ClassUtils.getQualifiedMethodName(reference.getMethod());
        return expectedDescription.equals(scheduledTask.getTask().getRunnable().toString());
    }
}
