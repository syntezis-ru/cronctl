package ru.syntezis.cronctl.core.execution;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import ru.syntezis.cronctl.domain.execution.ExecutionPage;
import ru.syntezis.cronctl.domain.execution.ExecutionQuery;
import ru.syntezis.cronctl.domain.execution.ScheduledTaskHealth;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.enums.ExecutionSource;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;
import ru.syntezis.cronctl.properties.CronctlProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/** Bounded, thread-safe, process-local implementation of {@link ExecutionStore}. */
@RequiredArgsConstructor
public class InMemoryExecutionStore implements ExecutionStore {

    private final Map<UUID, TaskExecution> executions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledTaskHealth> scheduledHealth = new ConcurrentHashMap<>();
    private final Set<UUID> countedTerminalExecutions = ConcurrentHashMap.newKeySet();
    private final Lock retentionLock = new ReentrantLock();
    private final CronctlProperties.History properties;

    @Override
    public void save(TaskExecution execution) {
        executions.put(execution.getExecutionId(), execution);
        updateScheduledHealth(execution);
        if (execution.isTerminal()) {
            enforceMaximumEntries();
        }
    }

    @Override
    public Optional<TaskExecution> findById(UUID executionId) {
        return Optional.ofNullable(executions.get(executionId));
    }

    @Override
    public ExecutionPage findAll(ExecutionQuery query) {
        int page = Math.max(0, query.getPage());
        int size = Math.min(200, Math.max(1, query.getSize()));
        List<TaskExecution> matching = executions.values().stream()
                .filter(execution -> matchesQuery(execution, query))
                .sorted(Comparator.comparing(TaskExecution::getCreatedAt).reversed())
                .toList();
        long offset = (long) page * size;
        if (offset >= matching.size()) {
            return new ExecutionPage(List.of(), matching.size(), page, size);
        }

        int fromIndex = (int) offset;
        int toIndex = Math.min(fromIndex + size, matching.size());
        return new ExecutionPage(List.copyOf(matching.subList(fromIndex, toIndex)), matching.size(), page, size);
    }

    @Override
    public Optional<TaskExecution> findRunningScheduled(String taskKey) {
        return executions.values().stream()
                .filter(execution -> execution.getSource() == ExecutionSource.SCHEDULED)
                .filter(execution -> execution.getStatus() == TaskExecutionStatus.RUNNING)
                .filter(execution -> execution.getTaskKey().equals(taskKey))
                .max(Comparator.comparing(TaskExecution::getStartedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    @Override
    public ScheduledTaskHealth getScheduledHealth(String taskKey) {
        return scheduledHealth.getOrDefault(taskKey, ScheduledTaskHealth.EMPTY);
    }

    @Override
    public void deleteExpired(Instant threshold) {
        retentionLock.lock();
        try {
            executions.values().stream()
                    .filter(TaskExecution::isTerminal)
                    .filter(execution -> execution.getFinishedAt() != null)
                    .filter(execution -> execution.getFinishedAt().isBefore(threshold))
                    .toList()
                    .forEach(this::remove);
        } finally {
            retentionLock.unlock();
        }
    }

    private boolean matchesQuery(TaskExecution execution, ExecutionQuery query) {
        return matchesFilter(query.getTaskKey(), execution.getTaskKey())
                && matchesFilter(query.getStatus(), execution.getStatus())
                && matchesFilter(query.getSource(), execution.getSource())
                && matchesFilter(query.getNodeId(), execution.getNodeId())
                && matchesFilter(query.getParentExecutionId(), execution.getParentExecutionId())
                && (query.getFrom() == null || !execution.getCreatedAt().isBefore(query.getFrom()))
                && (query.getTo() == null || execution.getCreatedAt().isBefore(query.getTo()));
    }

    private boolean matchesFilter(@Nullable Object expected, @Nullable Object actual) {
        return expected == null || expected.equals(actual);
    }

    private void updateScheduledHealth(TaskExecution execution) {
        if (execution.getSource() != ExecutionSource.SCHEDULED
                || !execution.isTerminal()
                || !countedTerminalExecutions.add(execution.getExecutionId())) {
            return;
        }

        scheduledHealth.compute(execution.getTaskKey(), (taskKey, previous) -> {
            ScheduledTaskHealth current = previous == null ? ScheduledTaskHealth.EMPTY : previous;
            Instant executionAt = execution.getStartedAt() == null
                    ? execution.getCreatedAt()
                    : execution.getStartedAt();
            Instant lastSuccessAt = current.getLastSuccessAt();
            int consecutiveFailures = current.getConsecutiveFailures();
            if (execution.getStatus() == TaskExecutionStatus.SUCCEEDED) {
                lastSuccessAt = executionAt;
                consecutiveFailures = 0;
            } else if (execution.getStatus() == TaskExecutionStatus.FAILED
                    || execution.getStatus() == TaskExecutionStatus.TIMED_OUT) {
                consecutiveFailures++;
            }
            return new ScheduledTaskHealth(executionAt, execution.getStatus(), lastSuccessAt, consecutiveFailures);
        });
    }

    private void enforceMaximumEntries() {
        retentionLock.lock();
        try {
            List<TaskExecution> terminalExecutions = executions.values().stream()
                    .filter(TaskExecution::isTerminal)
                    .sorted(Comparator.comparing(TaskExecution::getCreatedAt))
                    .toList();
            int excessEntries = terminalExecutions.size() - properties.getMaxEntries();
            if (excessEntries <= 0) {
                return;
            }

            List<TaskExecution> removals = new ArrayList<>(excessEntries);
            for (TaskExecution execution : terminalExecutions) {
                if (removals.size() >= excessEntries) {
                    break;
                }
                removals.add(execution);
            }
            removals.forEach(this::remove);
        } finally {
            retentionLock.unlock();
        }
    }

    private void remove(TaskExecution execution) {
        executions.remove(execution.getExecutionId(), execution);
        countedTerminalExecutions.remove(execution.getExecutionId());
    }

}
