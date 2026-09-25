package ru.syntezis.cronctl.core.sync;

import lombok.extern.slf4j.Slf4j;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.Task;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/** Invokes registered {@code @Scheduled} methods synchronously via reflection. */
@Slf4j
public class BlockingTaskExecutor {

    public TaskInvocationResult invoke(Task task, UUID executionId) {
        String taskKey = task.getTaskKey();
        String methodName = task.getDetails().getMethodName();
        ScheduledMethodReference reference = task.getReference();
        Object bean = reference.getBean();
        Method method = reference.getMethod();

        log.info("Executing task key: {}, execution id: {}, name: {}", taskKey, executionId, methodName);
        try {
            method.setAccessible(true); // NOSONAR
            method.invoke(bean);
            return TaskInvocationResult.succeeded();
        } catch (InvocationTargetException e) {
            Throwable cause = Optional.ofNullable(e.getCause()).orElse(e);
            return TaskInvocationResult.failed(cause);
        } catch (IllegalAccessException e) {
            return TaskInvocationResult.failed(e);
        }
    }
}
