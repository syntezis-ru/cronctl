package ru.syntezis.cronctl.core;

import lombok.extern.slf4j.Slf4j;
import ru.syntezis.cronctl.domain.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

@Slf4j
public class TaskExecutor {

    public TaskExecutionDetails executeTask(Task task) {
        ScheduledMethod scheduledMethod = task.getMethod();
        ScheduledMethodDetails details = scheduledMethod.getDetails();

        UUID methodId = details.getId();
        String methodName = details.getMethodName();

        ScheduledMethodReference reference = scheduledMethod.getReference();
        Object bean = reference.getBean();
        Method method = reference.getMethod();

        TaskExecutionDetails executionDetails = TaskExecutionDetails.prepare(methodId);
        UUID executionId = executionDetails.getExecutionId();
        log.info("Executing task id: {}. Scheduled method id: {}, name: {}", executionId, methodId, methodName);

        try {
            method.setAccessible(true); // NOSONAR
            executionDetails.execute();
            method.invoke(bean);
            executionDetails.succeeded();
            log.info("Task with id: {} executed successfully. Completed in {} mills", executionId, executionDetails.getExecutionDurationMills());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            log.error("Task with id: {} failed with exception message: {}", executionId, cause.getMessage(), cause);
            executionDetails.failed(cause, cause.getMessage());
        } catch (IllegalAccessException e) {
            log.error("Task with id: {} failed with exception message: {}", executionId, e.getMessage(), e);
            executionDetails.failed(e, e.getMessage());
        }

        return executionDetails;
    }
}
