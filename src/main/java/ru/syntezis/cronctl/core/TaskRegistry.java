package ru.syntezis.cronctl.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.exception.TaskAlreadyExistsInRegistryException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;

/**
 * Thread-safe in-memory registry of all {@code @Scheduled} methods discovered at startup.
 *
 * <p>Backed by a {@link ConcurrentHashMap}; individual {@link #add} calls are atomic.
 * The {@link #addAll} operation is not atomic as a whole — if one entry fails,
 * previously added entries remain registered.
 */
@Slf4j
public class TaskRegistry {

    private final Map<UUID, Task> tasks = new ConcurrentHashMap<>();

    /**
     * Registers a scheduled method under the given UUID.
     *
     * @param id   unique identifier for the task
     * @param task scheduled method to register
     * @throws TaskAlreadyExistsInRegistryException if a method with the same {@code id} is already registered
     */
    public void add(UUID id, Task task) {
        String methodName = task.getDetails().getMethodName();
        log.debug("Adding scheduled method to registry, id = {}, name = {}", id, methodName);

        Task existing = tasks.putIfAbsent(id, task);

        if (existing != null) {
            log.error("Scheduled method with id = {}, name = {} already exists in registry", id, methodName);
            throw new TaskAlreadyExistsInRegistryException(
                    format("Scheduled method with id = %s, name = %s already exists in registry", id, methodName)
            );
        }

        log.debug("Scheduled method with id = {}, name = {} has been registered", id, methodName);
    }

    /**
     * Registers multiple scheduled methods. Delegates to {@link #add} for each entry.
     *
     * @param entries list of (UUID, Task) pairs to register
     * @throws TaskAlreadyExistsInRegistryException if any entry's UUID is already registered
     */
    public void addAll(List<Pair<UUID, Task>> entries) {
        log.debug("Adding {} scheduled methods to registry", entries.size());
        entries.forEach(
                e -> add(e.getLeft(), e.getRight())
        );
    }

    /**
     * Finds a registered task by its UUID.
     *
     * @param id UUID to look up
     * @return an {@link Optional} containing the task, or empty if not found
     */
    public Optional<Task> getById(UUID id) {
        Task task = tasks.get(id);
        if (task == null) {
            log.debug("Scheduled method with id = {} not found", id);
            return Optional.empty();
        }

        log.debug("Scheduled method with id = {}, name = {} has been found by id", id, task.getDetails().getMethodName());

        return Optional.of(task);
    }

    /**
     * Returns all tasks carrying the given tag.
     *
     * @param tag tag to filter by
     * @return tasks that include {@code tag}; empty list if none match
     */
    public List<Task> getByTag(String tag) {
        return tasks.values().stream()
                .filter(t -> t.getTags().contains(tag))
                .toList();
    }

    /**
     * Returns all tasks belonging to the given group.
     *
     * @param group group name to filter by
     * @return tasks whose group equals {@code group}; empty list if none match
     */
    public List<Task> getByGroup(String group) {
        return tasks.values().stream()
                .filter(t -> t.getGroup().equals(group))
                .toList();
    }

    /**
     * Returns {@code true} if a task with the given UUID is registered.
     *
     * @param id UUID to check
     * @return {@code true} if present, {@code false} otherwise
     */
    public boolean contains(UUID id) {
        return tasks.containsKey(id);
    }

    /**
     * Returns a snapshot of all registered tasks.
     *
     * @return list of all tasks; empty if none are registered
     */
    public List<Task> getAll() {
        return tasks.values().stream()
                .toList();
    }

    /**
     * Removes all registered tasks. Primarily used in tests to reset state between runs.
     */
    public void clear() {
        tasks.clear();
    }
}
