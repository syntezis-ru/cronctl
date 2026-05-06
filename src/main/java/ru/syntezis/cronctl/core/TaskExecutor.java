package ru.syntezis.cronctl.core;

import lombok.extern.slf4j.Slf4j;
import ru.syntezis.cronctl.domain.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.Task;
import ru.syntezis.cronctl.domain.TaskExecutionDetails;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

@Slf4j
public class TaskExecutor {

    public TaskExecutionDetails executeTask(Task task) {
        UUID taskId = UUID.randomUUID();

        ScheduledMethodDetails details = task.getDetails();
        UUID id = details.getId();
        String methodName = details.getMethodName();
        ScheduledMethodReference reference = task.getReference();
        Object bean = reference.getBean();
        Method method = reference.getMethod();

        log.info("Executing task with id:{}. Scheduled method id:{}, method name:{}", taskId, id, methodName);
        TaskExecutionDetails executionDetails = TaskExecutionDetails.prepare();
        try {
            method.setAccessible(true);
            executionDetails.execute();
            method.invoke(bean);
        } catch (InvocationTargetException | IllegalAccessException e) {
            log.error("Task with id:{} failed with exception message:{}", taskId, e.getMessage(), e);
            executionDetails.failed(e, e.getMessage());
        }

        log.info("Task with id:{} executed successfully", taskId);

        return executionDetails;
    }
}
