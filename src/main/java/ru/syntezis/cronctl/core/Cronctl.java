package ru.syntezis.cronctl.core;

import lombok.RequiredArgsConstructor;
import ru.syntezis.cronctl.domain.task.Task;

import java.util.List;
import java.util.Optional;

/**
 * Main facade for managing registered {@code @Scheduled} tasks.
 *
 * <p>Provides operations to list, look up, and manually execute tasks
 * that were discovered by {@link ru.syntezis.cronctl.bpp.ScheduleAnnotationBeanPostProcessor}
 * at application startup.
 */
@RequiredArgsConstructor
public class Cronctl {

    private final TaskRegistry registry;

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
     * @param taskKey stable task key to look up
     * @return {@code true} if the task is registered, {@code false} otherwise
     */
    public boolean taskExists(String taskKey) {
        return registry.contains(taskKey);
    }

    /**
     * Finds a registered task by its stable key.
     *
     * @param taskKey stable task key to look up
     * @return an {@link Optional} containing the task, or empty if not found
     */
    public Optional<Task> getById(String taskKey) {
        return registry.getById(taskKey);
    }
}
