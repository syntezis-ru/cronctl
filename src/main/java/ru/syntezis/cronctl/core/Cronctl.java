package ru.syntezis.cronctl.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.syntezis.cronctl.domain.Task;
import ru.syntezis.cronctl.domain.TaskExecutionDetails;
import ru.syntezis.cronctl.scan.TaskRegistry;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class Cronctl {

    private final TaskRegistry registry;
    private final TaskExecutor executor;

    public List<Task> getAllTasks() {
        return registry.getAll();
    }

    public TaskExecutionDetails executeTaskByMethodName(String methodName) {
        Task task = registry.getByMethodName(methodName);
        return executor.executeTask(task);
    }

    public TaskExecutionDetails executeTaskById(UUID id) {
        Task task = registry.getByID(id);
        return executor.executeTask(task);
    }
}
