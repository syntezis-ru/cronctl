package ru.syntezis.cronctl.core;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Resolves the scheduler used when cronctl resumes a paused task across supported Spring versions.
 */
@RequiredArgsConstructor
@Slf4j
public class CompatibleTaskScheduler {

    private static final String DEFAULT_TASK_SCHEDULER_BEAN_NAME = "taskScheduler";
    private static final String TASK_SCHEDULER_ROUTER_CLASS_NAME =
            "org.springframework.scheduling.config.TaskSchedulerRouter";

    private final ObjectProvider<TaskScheduler> taskSchedulerProvider;
    private final BeanFactory beanFactory;

    @Nullable
    private volatile TaskScheduler taskScheduler;

    @Nullable
    private volatile DisposableBean ownedScheduler;

    /** Returns a scheduler compatible with the Spring version available at runtime. */
    public TaskScheduler getTaskScheduler() {
        TaskScheduler current = taskScheduler;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            TaskScheduler resolvedScheduler = taskScheduler;
            if (resolvedScheduler == null) {
                resolvedScheduler = createTaskScheduler();
                taskScheduler = resolvedScheduler;
            }
            return resolvedScheduler;
        }
    }

    private TaskScheduler createTaskScheduler() {
        TaskScheduler router = createTaskSchedulerRouter();
        if (router != null) {
            return router;
        }

        TaskScheduler uniqueScheduler = taskSchedulerProvider.getIfUnique();
        if (uniqueScheduler != null) {
            return uniqueScheduler;
        }

        if (beanFactory.containsBean(DEFAULT_TASK_SCHEDULER_BEAN_NAME)) {
            return beanFactory.getBean(DEFAULT_TASK_SCHEDULER_BEAN_NAME, TaskScheduler.class);
        }

        ThreadPoolTaskScheduler localScheduler = new ThreadPoolTaskScheduler();
        localScheduler.setPoolSize(1);
        localScheduler.setThreadNamePrefix("cronctl-scheduler-");
        localScheduler.setRemoveOnCancelPolicy(true);
        localScheduler.initialize();
        ownedScheduler = localScheduler;
        log.info("No application TaskScheduler found; cronctl created a local scheduler for resumed tasks");
        return localScheduler;
    }

    private @Nullable TaskScheduler createTaskSchedulerRouter() {
        ClassLoader classLoader = CompatibleTaskScheduler.class.getClassLoader();
        if (!ClassUtils.isPresent(TASK_SCHEDULER_ROUTER_CLASS_NAME, classLoader)) {
            return null;
        }

        try {
            Class<?> routerClass = ClassUtils.forName(TASK_SCHEDULER_ROUTER_CLASS_NAME, classLoader);
            Object router = routerClass.getDeclaredConstructor().newInstance();
            invoke(routerClass.getMethod("setBeanName", String.class), router, "cronctlTaskSchedulerRouter");
            invoke(routerClass.getMethod("setBeanFactory", BeanFactory.class), router, beanFactory);
            if (router instanceof DisposableBean disposableBean) {
                ownedScheduler = disposableBean;
            }
            return (TaskScheduler) router;
        } catch (ReflectiveOperationException | LinkageError e) {
            log.warn("Spring TaskSchedulerRouter is unavailable; falling back to an application scheduler", e);
            return null;
        }
    }

    private void invoke(Method method, Object target, Object argument)
            throws InvocationTargetException, IllegalAccessException {
        method.invoke(target, argument);
    }

    /** Releases only a scheduler created and owned by cronctl. */
    @PreDestroy
    public synchronized void shutdown() throws Exception {
        DisposableBean schedulerToDestroy = ownedScheduler;
        ownedScheduler = null;
        taskScheduler = null;
        if (schedulerToDestroy != null) {
            schedulerToDestroy.destroy();
        }
    }

}
