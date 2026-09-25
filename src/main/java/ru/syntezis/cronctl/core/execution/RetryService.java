package ru.syntezis.cronctl.core.execution;

import lombok.RequiredArgsConstructor;
import ru.syntezis.cronctl.core.TaskRegistry;
import ru.syntezis.cronctl.domain.execution.ExecutionPage;
import ru.syntezis.cronctl.domain.execution.ExecutionQuery;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Provides leaf-safe manual retry operations over execution history. */
@RequiredArgsConstructor
public class RetryService {

    private final ExecutionStore executionStore;
    private final TaskRegistry taskRegistry;
    private final RetryCoordinator retryCoordinator;
    private final Set<UUID> retryReservations = ConcurrentHashMap.newKeySet();

    public boolean isRetryable(TaskExecution execution) {
        return isFailure(execution)
                && taskRegistry.contains(execution.getTaskKey())
                && !hasChild(execution.getExecutionId());
    }

    public Optional<TaskExecution> retry(Task task, TaskExecution parentExecution) {
        UUID parentExecutionId = parentExecution.getExecutionId();
        if (!retryReservations.add(parentExecutionId)) {
            return Optional.empty();
        }
        try {
            if (!isFailure(parentExecution) || hasChild(parentExecutionId)) {
                return Optional.empty();
            }
            return Optional.of(retryCoordinator.scheduleManual(task, parentExecution));
        } finally {
            retryReservations.remove(parentExecutionId);
        }
    }

    public List<TaskExecution> retryAllFailed() {
        return taskRegistry.getAll().stream()
                .map(task -> latestFailure(task.getTaskKey()).map(execution -> Map.entry(task, execution)))
                .flatMap(Optional::stream)
                .map(entry -> retry(entry.getKey(), entry.getValue()))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<TaskExecution> latestFailure(String taskKey) {
        return List.of(TaskExecutionStatus.FAILED, TaskExecutionStatus.TIMED_OUT).stream()
                .map(status -> latest(taskKey, status))
                .flatMap(Optional::stream)
                .max(Comparator.comparing(TaskExecution::getCreatedAt));
    }

    private Optional<TaskExecution> latest(String taskKey, TaskExecutionStatus status) {
        ExecutionPage page = executionStore.findAll(ExecutionQuery.builder()
                .taskKey(taskKey)
                .status(status)
                .page(0)
                .size(1)
                .build());
        return page.getExecutions().stream().findFirst();
    }

    private boolean hasChild(UUID executionId) {
        return executionStore.findAll(ExecutionQuery.builder()
                .parentExecutionId(executionId)
                .page(0)
                .size(1)
                .build()).getTotal() > 0;
    }

    private boolean isFailure(TaskExecution execution) {
        return execution.getStatus() == TaskExecutionStatus.FAILED
                || execution.getStatus() == TaskExecutionStatus.TIMED_OUT;
    }

}
