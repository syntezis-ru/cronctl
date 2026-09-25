package ru.syntezis.cronctl.core.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.util.ClassUtils;
import ru.syntezis.cronctl.core.ScheduledTaskNextExecutionResolver;
import ru.syntezis.cronctl.core.TaskRegistry;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.scheduled.ScheduleDetails;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.util.ScheduleUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks the expected schedule slot used to calculate actual start delay. */
@Slf4j
@RequiredArgsConstructor
public class PlannedExecutionTracker implements ApplicationListener<ContextRefreshedEvent> {

    private final TaskRegistry taskRegistry;
    private final ObjectProvider<ScheduledTaskHolder> scheduledTaskHolderProvider;
    private final ScheduledTaskNextExecutionResolver scheduledTaskNextExecutionResolver;
    private final Map<String, Instant> plannedExecutions = new ConcurrentHashMap<>();

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        taskRegistry.getAll().forEach(this::synchronizeWithScheduler);
    }

    public @Nullable Instant getPlannedAt(String taskKey) {
        return plannedExecutions.get(taskKey);
    }

    public void executionStarted(Task task, TaskExecution execution) {
        if (execution.getPlannedAt() != null || !isFixedRateSchedule(task.getDetails().getSchedule())) {
            return;
        }

        nextFixedRateExecution(task.getDetails().getSchedule(), execution.getStartedAt())
                .ifPresent(nextExecution -> plannedExecutions.putIfAbsent(task.getTaskKey(), nextExecution));
    }

    public void executionCompleted(Task task, TaskExecution execution) {
        Instant finishedAt = execution.getFinishedAt();
        if (finishedAt == null) {
            return;
        }

        ScheduleDetails schedule = task.getDetails().getSchedule();
        Instant nextExecution = nextCronExecution(schedule, finishedAt)
                .or(() -> nextFixedDelayExecution(schedule, finishedAt))
                .or(() -> nextFixedRateExecution(schedule, execution.getPlannedAt()))
                .orElse(null);
        if (isFixedRateSchedule(schedule) && execution.getPlannedAt() == null) {
            Instant schedulerSnapshot = plannedExecutions.get(task.getTaskKey());
            if (schedulerSnapshot != null && schedulerSnapshot.isAfter(execution.getCreatedAt())) {
                return;
            }
            synchronizeWithScheduler(task);
            return;
        }
        update(task.getTaskKey(), nextExecution);
    }

    public void synchronizeWithScheduler(Task task) {
        Instant nextExecution = scheduledTaskHolderProvider.orderedStream()
                .flatMap(holder -> holder.getScheduledTasks().stream())
                .filter(scheduledTask -> matches(task, scheduledTask))
                .map(scheduledTaskNextExecutionResolver::resolve)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
        update(task.getTaskKey(), nextExecution);
    }

    public void clear(String taskKey) {
        plannedExecutions.remove(taskKey);
    }

    public void setPlannedAt(String taskKey, @Nullable Instant plannedAt) {
        update(taskKey, plannedAt);
    }

    private Optional<Instant> nextCronExecution(ScheduleDetails schedule, Instant finishedAt) {
        return Optional.ofNullable(ScheduleUtils.computeNextExecutionAt(schedule, finishedAt));
    }

    private Optional<Instant> nextFixedDelayExecution(ScheduleDetails schedule, Instant finishedAt) {
        return scheduleDuration(schedule.getFixedDelay(), schedule.getFixedDelayString(), schedule)
                .map(finishedAt::plus);
    }

    private Optional<Instant> nextFixedRateExecution(ScheduleDetails schedule, @Nullable Instant plannedAt) {
        if (plannedAt == null) {
            return Optional.empty();
        }
        return scheduleDuration(schedule.getFixedRate(), schedule.getFixedRateString(), schedule)
                .map(plannedAt::plus);
    }

    private boolean isFixedRateSchedule(ScheduleDetails schedule) {
        return schedule.getFixedRate() >= 0
                || (schedule.getFixedRateString() != null && !schedule.getFixedRateString().isBlank());
    }

    private Optional<Duration> scheduleDuration(long numericValue, @Nullable String stringValue,
                                                ScheduleDetails schedule) {
        if (stringValue != null && !stringValue.isBlank()) {
            try {
                return Optional.of(org.springframework.boot.convert.DurationStyle.detectAndParse(
                        stringValue, schedule.getTimeUnit().toChronoUnit()
                ));
            } catch (IllegalArgumentException e) {
                log.debug("Cannot parse schedule duration {}", stringValue, e);
                return Optional.empty();
            }
        }
        if (numericValue < 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.of(numericValue, schedule.getTimeUnit().toChronoUnit()));
    }

    private boolean matches(Task task, ScheduledTask scheduledTask) {
        String expectedDescription = ClassUtils.getQualifiedMethodName(task.getReference().getMethod());
        return expectedDescription.equals(scheduledTask.getTask().getRunnable().toString());
    }

    private void update(String taskKey, @Nullable Instant nextExecution) {
        if (nextExecution == null) {
            plannedExecutions.remove(taskKey);
        } else {
            plannedExecutions.put(taskKey, nextExecution);
        }
    }

}
