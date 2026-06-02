package ru.syntezis.cronctl.core;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.util.ScheduleUtils;

import java.time.Instant;

/**
 * Resolves the next scheduled execution time for a {@link Task}.
 *
 * <p>Primary path: iterates {@link ScheduledTaskHolder#getScheduledTasks()} to find the entry
 * matching this task's bean and method, then reads the next run time via
 * {@link ScheduledTask#nextExecution()}.
 * This covers all schedule types — cron, fixedRate, and fixedDelay.
 *
 * <p>Fallback (when no matching future is found): delegates to
 * {@link ScheduleUtils#computeNextExecutionAt} which uses {@code CronExpression} arithmetic.
 * This handles cron tasks even when {@link ScheduledTaskHolder} is unavailable.
 */
@RequiredArgsConstructor
public class NextExecutionTimeResolver {

    private final ObjectProvider<ScheduledTaskHolder> scheduledTaskHolderProvider;

    /**
     * Returns the next execution time for the given task, or {@code null} if it cannot be determined.
     *
     * @param task the task to inspect
     * @return next execution instant, or {@code null} for non-cron tasks without a live future
     */
    public @Nullable Instant computeNextExecutionAt(Task task) {
        ScheduledTaskHolder holder = scheduledTaskHolderProvider.getIfAvailable();
        if (holder != null) {
            ScheduledMethodReference ref = task.getReference();
            String expectedDesc = ref.getMethod().getDeclaringClass().getName() + "." + ref.getMethod().getName();
            for (ScheduledTask scheduledTask : holder.getScheduledTasks()) {
                if (!expectedDesc.equals(scheduledTask.getTask().getRunnable().toString())) continue;

                Instant nextExecution = scheduledTask.nextExecution();
                if (nextExecution != null) {
                    return nextExecution;
                }
            }
        }

        return ScheduleUtils.computeNextExecutionAt(task.getDetails().getSchedule());
    }
}
