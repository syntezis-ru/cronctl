package ru.syntezis.cronctl.core.sync;

import lombok.extern.slf4j.Slf4j;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.domain.task.TaskExecutionDetails;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

/**
 * Executes registered {@code @Scheduled} methods on demand via reflection.
 *
 * <p>Each invocation captures start/end timestamps, resolves the final execution status,
 * and wraps any exception into {@link TaskExecutionDetails.FailDetails} without rethrowing.
 */
@Slf4j
public class BlockingTaskExecutor {

    /**
     * Invokes the {@code @Scheduled} method referenced by the given task.
     *
     * <p>The method is called synchronously on the current thread.
     * If the method throws an exception, it is caught and recorded in the returned details
     * with status {@link ru.syntezis.cronctl.enums.TaskExecutionStatus#FAILED}; it is never rethrown.
     *
     * @param task task whose underlying method should be executed
     * @return execution details including status, timing in millis/nanos, and failure information if any
     */
    public TaskExecutionDetails executeTask(Task task) {
        ScheduledMethodDetails details = task.getDetails();

        UUID methodId = details.getId();
        String methodName = details.getMethodName();

        ScheduledMethodReference reference = task.getReference();
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
            Throwable cause = Optional.ofNullable(e.getCause()).orElse(e);
            log.error("Task with id: {} failed with exception message: {}", executionId, cause.getMessage(), cause);
            executionDetails.failed(cause, cause.getMessage());
        } catch (IllegalAccessException e) {
            log.error("Task with id: {} failed with exception message: {}", executionId, e.getMessage(), e);
            executionDetails.failed(e, e.getMessage());
        }

        return executionDetails;
    }
}
