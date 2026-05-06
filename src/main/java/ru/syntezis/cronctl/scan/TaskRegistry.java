package ru.syntezis.cronctl.scan;

import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import ru.syntezis.cronctl.domain.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.Task;
import ru.syntezis.cronctl.exception.TaskAlreadyExistsInRegistryException;
import ru.syntezis.cronctl.exception.TaskNotFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.String.format;

@Slf4j
public class TaskRegistry {

    private static final Map<ScheduledMethodDetails, ScheduledMethodReference> scheduledMethods = new ConcurrentHashMap<>();

    @Synchronized
    public void add(ScheduledMethodDetails details, ScheduledMethodReference reference) {
        UUID id = details.getId();
        String methodName = details.getMethodName();

        if (scheduledMethods.containsKey(details)) {
            log.error("Scheduled method with id={}, name={} already exists in registry", id,  methodName);
            throw new TaskAlreadyExistsInRegistryException(
                    format("Scheduled method with id=%s, name=%s already exists in registry", id, methodName)
            );
        }

        scheduledMethods.put(details, reference);
        log.debug("Scheduled method with id={}, name={} has been registered", id, methodName);
    }

    public Task getByMethodName(String methodName) {
        Map.Entry<ScheduledMethodDetails, ScheduledMethodReference> entry = scheduledMethods.entrySet().stream()
                .filter(es -> es.getKey().getMethodName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Scheduled method with name={} not found", methodName);
                    return new TaskNotFoundException(format("Scheduled method with id=%s not found", methodName));
                });

        return new Task(entry.getKey(), entry.getValue());
    }

    public Task getByID(UUID id) {
        Map.Entry<ScheduledMethodDetails, ScheduledMethodReference> entry = scheduledMethods.entrySet().stream()
                .filter(es -> es.getKey().getId().equals(id))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Scheduled method with id={} not found", id);
                    return new TaskNotFoundException(format("Scheduled method with id=%s not found", id));
                });

        return new Task(entry.getKey(), entry.getValue());
    }

    public List<Task> getAll() {
        return scheduledMethods.entrySet().stream()
                .map(e -> new Task(e.getKey(), e.getValue()))
                .toList();
    }

    public void clear() {
        scheduledMethods.clear();
    }
}
