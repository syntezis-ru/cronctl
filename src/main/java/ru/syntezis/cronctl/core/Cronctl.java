package ru.syntezis.cronctl.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.domain.task.TaskExecutionDetails;
import ru.syntezis.cronctl.exception.TaskNotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.lang.String.format;

/**
 * Main facade for managing registered {@code @Scheduled} tasks.
 *
 * <p>Provides operations to list, look up, and manually execute tasks
 * that were discovered by {@link ru.syntezis.cronctl.bpp.ScheduleAnnotationBeanPostProcessor}
 * at application startup.
 */
@RequiredArgsConstructor
@Slf4j
public class Cronctl {

    private final TaskRegistry registry;
    private final TaskExecutor executor;

    /**
     * Returns all tasks currently registered in the registry.
     *
     * @return unmodifiable snapshot of all registered tasks; empty list if none exist
     */
    public List<Task> getAllTasks() {
        return registry.getAll();
    }

    /**
     * Returns all tasks carrying the given tag.
     *
     * @param tag tag to filter by
     * @return tasks that include {@code tag}; empty list if none match
     */
    public List<Task> getByTag(String tag) {
        return registry.getByTag(tag);
    }

    /**
     * Returns all tasks belonging to the given group.
     *
     * @param group group name to filter by
     * @return tasks whose group equals {@code group}; empty list if none match
     */
    public List<Task> getByGroup(String group) {
        return registry.getByGroup(group);
    }

    /**
     * Checks whether a task with the given id is registered.
     *
     * @param id task UUID to look up
     * @return {@code true} if the task is registered, {@code false} otherwise
     */
    public boolean taskExists(UUID id) {
        return registry.contains(id);
    }

    /**
     * Finds a registered task by its UUID.
     *
     * @param id task UUID to look up
     * @return an {@link Optional} containing the task, or empty if not found
     */
    public Optional<Task> getById(UUID id) {
        return registry.getById(id);
    }

    /**
     * Manually triggers the {@code @Scheduled} method associated with the given task id.
     *
     * <p>The method is invoked synchronously on the calling thread.
     * Execution metrics and outcome are captured in the returned {@link TaskExecutionDetails}.
     *
     * @param id UUID of the task to execute
     * @return execution details including status, timing, and failure information if any
     * @throws TaskNotFoundException if no task with the given id is registered
     */
    public TaskExecutionDetails executeTaskByID(UUID id) {
        Task task = registry.getById(id)
                .orElseThrow(() -> {
                            log.error("Task with id = {} not found", id);
                            return new TaskNotFoundException(format("Task with id = %s not found", id));
                        }
                );

        return executor.executeTask(task);
    }
}
