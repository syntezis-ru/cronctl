package ru.syntezis.cronctl.core.async;

import lombok.extern.slf4j.Slf4j;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory store of all submitted async executions.
 *
 * <p><b>Memory:</b> executions are never evicted. For long-running applications
 * with frequent manual triggers, the registry will grow without bound.
 * Consider periodic cleanup or an external TTL mechanism if needed.
 */
@Slf4j
public class ExecutionRegistry {

    private final ConcurrentHashMap<UUID, TaskExecution> executions = new ConcurrentHashMap<>();

    /**
     * Registers a newly created execution.
     *
     * @param execution execution to register
     */
    public void register(TaskExecution execution) {
        executions.put(execution.getExecutionId(), execution);
        log.debug("Registered execution {}", execution.getExecutionId());
    }

    /**
     * Looks up an execution by its ID.
     *
     * @param executionId ID to look up
     * @return an {@link Optional} containing the execution, or empty if not found
     */
    public Optional<TaskExecution> getById(UUID executionId) {
        return Optional.ofNullable(executions.get(executionId));
    }

    /**
     * Returns all registered executions as an unordered snapshot.
     *
     * @return unmodifiable collection of all executions
     */
    public Collection<TaskExecution> getAll() {
        return List.copyOf(executions.values());
    }

    /**
     * Returns all executions matching the given status.
     *
     * @param status status to filter by
     * @return unmodifiable list of matching executions
     */
    public List<TaskExecution> getByStatus(TaskExecutionStatus status) {
        return executions.values().stream()
                .filter(execution -> execution.getState() == status)
                .toList();
    }

    /**
     * Requests cancellation of the execution with the given ID.
     *
     * <p>Returns an {@link Optional} that allows callers to distinguish three outcomes
     * without performing a redundant lookup:
     * <ul>
     *   <li>empty — no execution found for the given ID</li>
     *   <li>{@code true}  — cancellation was accepted (transition to CANCELLED/TIMED_OUT)</li>
     *   <li>{@code false} — execution is already in a terminal state</li>
     * </ul>
     *
     * @param executionId ID of the execution to cancel
     * @return empty if not found; {@code true} if accepted; {@code false} if already terminal
     */
    public Optional<Boolean> cancel(UUID executionId) {
        return getById(executionId)
                .map(execution -> {
                    boolean requested = execution.requestCancellation(false);
                    if (requested) {
                        execution.cancelFuture();
                    }
                    return requested;
                });
    }
}
