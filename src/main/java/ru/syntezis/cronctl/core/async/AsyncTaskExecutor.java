package ru.syntezis.cronctl.core.async;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.core.execution.ExecutionLifecycleService;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.ExecutionSource;
import ru.syntezis.cronctl.properties.CronctlProperties;

import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Submits registered tasks for tracked asynchronous execution on a bounded thread pool. */
@Slf4j
public class AsyncTaskExecutor {

    private final ExecutionLifecycleService lifecycleService;
    private final long defaultTimeoutSeconds;
    private final ThreadPoolExecutor threadPool;
    private final ScheduledExecutorService timeoutScheduler;

    public AsyncTaskExecutor(ExecutionLifecycleService lifecycleService,
                             CronctlProperties.Executor executorProperties) {
        this.lifecycleService = lifecycleService;
        this.defaultTimeoutSeconds = executorProperties.getTimeoutSeconds();
        this.threadPool = new ThreadPoolExecutor(
                executorProperties.getThreadPoolSize(),
                executorProperties.getThreadPoolSize(),
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(executorProperties.getQueueCapacity()),
                daemonFactory("cronctl-executor-"),
                new ThreadPoolExecutor.AbortPolicy()
        );
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(
                daemonFactory("cronctl-timeout-")
        );
    }

    public TaskExecution submit(Task task) {
        TaskExecution execution = lifecycleService.createQueued(
                task.getTaskKey(), ExecutionSource.MANUAL_ASYNC, null
        );
        return submit(task, execution);
    }

    /** Submits an existing queued execution, used by delayed retry attempts. */
    public TaskExecution submit(Task task, TaskExecution execution) {
        long effectiveTimeout = task.getTimeoutSeconds() == CronctlTask.USE_GLOBAL_TIMEOUT
                ? defaultTimeoutSeconds
                : task.getTimeoutSeconds();

        try {
            Future<?> future = threadPool.submit(() -> lifecycleService.execute(execution, task));
            execution.setFuture(future);
            if (execution.isCancellationRequested()) {
                future.cancel(true);
            }
        } catch (RejectedExecutionException e) {
            lifecycleService.skip(execution, "QUEUE_REJECTED");
            return execution;
        }

        if (effectiveTimeout > 0) {
            scheduleTimeout(execution, effectiveTimeout);
        }
        log.info("Task {} submitted for async execution, executionId={}",
                task.getTaskKey(), execution.getExecutionId());
        return execution;
    }

    private void scheduleTimeout(TaskExecution execution, long timeoutSeconds) {
        timeoutScheduler.schedule(() -> {
            boolean requested = lifecycleService.requestCancellation(execution, true);
            if (requested) {
                log.info("Execution {} timed out after {}s", execution.getExecutionId(), timeoutSeconds);
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
    }

    private static ThreadFactory daemonFactory(String namePrefix) {
        AtomicLong counter = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable, namePrefix + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Shuts down the thread pool and timeout scheduler on application context close. */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down AsyncTaskExecutor");
        threadPool.shutdown();
        timeoutScheduler.shutdown();
    }
}
