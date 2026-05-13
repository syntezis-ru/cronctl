package ru.syntezis.cronctl.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.syntezis.cronctl.domain.Task;
import ru.syntezis.cronctl.domain.TaskExecutionDetails;
import ru.syntezis.cronctl.exception.TaskNotFoundException;
import ru.syntezis.cronctl.scan.TaskRegistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.lang.String.format;

@RequiredArgsConstructor
@Slf4j
public class Cronctl {

    private final TaskRegistry registry;
    private final TaskExecutor executor;

    public List<Task> getAllTasks() {
        return registry.getAll();
    }

    public boolean taskExists(UUID id) {
        return registry.contains(id);
    }

    public Optional<Task> getById(UUID id) {
        return registry.getById(id);
    }

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
