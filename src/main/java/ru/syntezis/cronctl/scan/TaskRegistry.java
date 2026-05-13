package ru.syntezis.cronctl.scan;

import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import ru.syntezis.cronctl.domain.ScheduledMethod;
import ru.syntezis.cronctl.domain.Task;
import ru.syntezis.cronctl.exception.TaskAlreadyExistsInRegistryException;
import ru.syntezis.cronctl.exception.TaskNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;

@Slf4j
public class TaskRegistry {

    private final Map<UUID, ScheduledMethod> scheduledMethods = new ConcurrentHashMap<>();

    public void add(UUID id, ScheduledMethod method) {
        String methodName = method.getDetails().getMethodName();
        log.debug("Adding scheduled method to registry, id = {}, name = {}", id, methodName);

        ScheduledMethod existing = scheduledMethods.putIfAbsent(id, method);

        if (existing != null) {
            log.error("Scheduled method with id = {}, name = {} already exists in registry", id, methodName);
            throw new TaskAlreadyExistsInRegistryException(
                    format("Scheduled method with id = %s, name = %s already exists in registry", id, methodName)
            );
        }

        log.debug("Scheduled method with id = {}, name = {} has been registered", id, methodName);
    }

    public void addAll(List<Pair<UUID, ScheduledMethod>> entries) {
        log.debug("Adding {} scheduled methods to registry", entries.size());
        entries.forEach(
                e -> add(e.getLeft(), e.getRight())
        );
    }

    public Optional<Task> getById(UUID id) {
        ScheduledMethod method = scheduledMethods.get(id);
        if (method == null) {
            log.error("Scheduled method with id = {} not found", id);
            return Optional.empty();
        }

        log.debug("Scheduled method with id = {}, name = {} has been found by id", id, method.getDetails().getMethodName());

        return Optional.of(new Task(method));
    }

    public boolean contains(UUID id) {
        return scheduledMethods.containsKey(id);
    }

    public List<Task> getAll() {
        return scheduledMethods.values().stream()
                .map(Task::new)
                .toList();
    }

    public void clear() {
        scheduledMethods.clear();
    }
}
