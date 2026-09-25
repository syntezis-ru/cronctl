package ru.syntezis.cronctl.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.AutomaticTrackingStatus;
import ru.syntezis.cronctl.exception.TaskAlreadyExistsInRegistryException;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

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

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final Map<String, List<Task>> scheduledMethods = new ConcurrentHashMap<>();

    /**
     * Registers a scheduled method under the given stable task key.
     *
     * @param taskKey unique stable key for the task
     * @param task scheduled method to register
     * @throws TaskAlreadyExistsInRegistryException if a method with the same {@code taskKey} is already registered
     */
    public void add(String taskKey, Task task) {
        String methodName = task.getDetails().getMethodName();
        log.debug("Adding scheduled method to registry, taskKey = {}, name = {}", taskKey, methodName);

        Task existing = tasks.putIfAbsent(taskKey, task);

        if (existing != null) {
            log.error("Scheduled method with taskKey = {}, name = {} already exists in registry", taskKey, methodName);
            throw new TaskAlreadyExistsInRegistryException(
                    format("Scheduled method with taskKey = %s, name = %s already exists in registry", taskKey, methodName)
            );
        }

        if (task.getReference() != null && task.getReference().getMethod() != null) {
            scheduledMethods.compute(methodKey(task.getReference().getMethod().getDeclaringClass(),
                            task.getReference().getMethod()),
                    (key, registeredTasks) -> {
                        List<Task> updatedTasks = registeredTasks == null
                                ? List.of(task)
                                : Stream.concat(registeredTasks.stream(), Stream.of(task)).toList();
                        if (updatedTasks.size() > 1) {
                            String message = "Multiple scheduled bean instances use the same class and method";
                            updatedTasks.forEach(registeredTask -> {
                                registeredTask.setAutomaticTrackingStatus(AutomaticTrackingStatus.AMBIGUOUS);
                                registeredTask.setAutomaticTrackingMessage(message);
                            });
                            log.warn("Automatic execution tracking is ambiguous for {}", key);
                        }
                        return updatedTasks;
                    });
        }

        log.debug("Scheduled method with taskKey = {}, name = {} has been registered", taskKey, methodName);
    }

    /**
     * Registers multiple scheduled methods. Delegates to {@link #add} for each entry.
     *
     * @param entries list of (task key, Task) pairs to register
     * @throws TaskAlreadyExistsInRegistryException if any entry's task key is already registered
     */
    public void addAll(List<Pair<String, Task>> entries) {
        log.debug("Adding {} scheduled methods to registry", entries.size());
        entries.forEach(
                e -> add(e.getLeft(), e.getRight())
        );
    }

    /**
     * Finds a registered task by its stable key.
     *
     * @param taskKey task key to look up
     * @return an {@link Optional} containing the task, or empty if not found
     */
    public Optional<Task> getById(String taskKey) {
        Task task = tasks.get(taskKey);
        if (task == null) {
            log.debug("Scheduled method with taskKey = {} not found", taskKey);
            return Optional.empty();
        }

        log.debug("Scheduled method with taskKey = {}, name = {} has been found", taskKey, task.getDetails().getMethodName());

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
     * Returns {@code true} if a task with the given key is registered.
     *
     * @param taskKey task key to check
     * @return {@code true} if present, {@code false} otherwise
     */
    public boolean contains(String taskKey) {
        return tasks.containsKey(taskKey);
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

    /** Resolves a scheduled observation to one unambiguous registered task. */
    public Optional<Task> getByScheduledMethod(Class<?> targetClass, Method method) {
        List<Task> matchingTasks = scheduledMethods.get(methodKey(targetClass, method));
        if (matchingTasks == null || matchingTasks.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(matchingTasks.get(0));
    }

    /**
     * Removes all registered tasks. Primarily used in tests to reset state between runs.
     */
    public void clear() {
        tasks.clear();
        scheduledMethods.clear();
    }

    private String methodKey(Class<?> targetClass, Method method) {
        return targetClass.getName() + "#" + method.getName()
                + Arrays.toString(method.getParameterTypes());
    }
}
