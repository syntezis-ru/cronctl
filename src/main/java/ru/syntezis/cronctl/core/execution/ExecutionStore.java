package ru.syntezis.cronctl.core.execution;

import ru.syntezis.cronctl.domain.execution.ExecutionPage;
import ru.syntezis.cronctl.domain.execution.ExecutionQuery;
import ru.syntezis.cronctl.domain.execution.ScheduledTaskHealth;
import ru.syntezis.cronctl.domain.execution.TaskExecution;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Pluggable, thread-safe storage contract for task execution history and scheduled health.
 * Implementations should treat {@link #save(TaskExecution)} as an upsert because the same
 * execution is saved after each lifecycle transition.
 */
public interface ExecutionStore {

    /** Creates or updates an execution using its execution ID as the identity. */
    void save(TaskExecution execution);

    /** Finds an execution by its random execution UUID. */
    Optional<TaskExecution> findById(UUID executionId);

    /** Finds a newest-first page matching the supplied query. */
    ExecutionPage findAll(ExecutionQuery query);

    /** Finds the currently running automatic execution for a task, if present. */
    Optional<TaskExecution> findRunningScheduled(String taskKey);

    /** Returns process or persistent scheduled-health aggregates for a task. */
    ScheduledTaskHealth getScheduledHealth(String taskKey);

    /**
     * Deletes terminal execution records completed before the given threshold.
     * Active executions and scheduled-health aggregates must not be deleted.
     */
    void deleteExpired(Instant threshold);

}
