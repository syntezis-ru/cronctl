package ru.syntezis.cronctl.core.execution;

import lombok.NoArgsConstructor;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.task.Task;

/** No-op retry lifecycle used when composing the service directly without auto-configuration. */
@NoArgsConstructor
public class DisabledRetryLifecycleHandler implements RetryLifecycleHandler {

    @Override
    public void scheduleAutomatic(Task task, TaskExecution failedExecution, Throwable failure) {
    }

    @Override
    public void cancelPending(TaskExecution execution) {
    }

}
