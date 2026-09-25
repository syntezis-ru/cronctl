package ru.syntezis.cronctl.core.execution;

import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.task.Task;

/** Retry callbacks invoked by the common execution lifecycle. */
public interface RetryLifecycleHandler {

    void scheduleAutomatic(Task task, TaskExecution failedExecution, Throwable failure);

    void cancelPending(TaskExecution execution);

}
