package ru.syntezis.cronctl.core.async;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import ru.syntezis.cronctl.core.sync.BlockingTaskExecutor;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.domain.task.TaskExecutionDetails;
import ru.syntezis.cronctl.properties.CronctlProperties;

import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Submits registered tasks for asynchronous execution on a bounded thread pool.
 *
 * <p>Each submission creates a {@link TaskExecution} entry tracked in {@link ExecutionRegistry}.
 * Timeout cancellation is handled by a dedicated scheduler that interrupts the executing thread
 * after the configured deadline.
 *
 * <p>The effective timeout per task is resolved as follows:
 * <ol>
 *   <li>If the task carries a non-zero {@code @CronctlTask(timeout=...)} — that value is used.</li>
 *   <li>Otherwise, {@code cronctl.executor.timeout-seconds} from configuration is used.</li>
 *   <li>If both are {@code 0}, no timeout is applied.</li>
 * </ol>
 */
@Slf4j
public class AsyncTaskExecutor {

    private final BlockingTaskExecutor syncExecutor;
    private final ExecutionRegistry executionRegistry;
    private final long defaultTimeoutSeconds;
    private final ThreadPoolExecutor threadPool;
    private final ScheduledExecutorService timeoutScheduler;

    /**
     * Creates an executor with a bounded thread pool and a dedicated timeout scheduler.
     *
     * @param syncExecutor       executor used to run the task body on the worker thread
     * @param executionRegistry  registry where execution state is tracked
     * @param executorProperties thread-pool and timeout configuration
     */
    public AsyncTaskExecutor(BlockingTaskExecutor syncExecutor,
                             ExecutionRegistry executionRegistry,
                             CronctlProperties.Executor executorProperties) {
        this.syncExecutor = syncExecutor;
        this.executionRegistry = executionRegistry;
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
                daemonFactory("cronctl-timeout")
        );
    }

    /**
     * Submits a task for async execution.
     *
     * @param task task to execute
     * @return execution ID that can be used to query status or request cancellation
     * @throws java.util.concurrent.RejectedExecutionException if the queue is full
     */
    public UUID submit(Task task) {
        TaskExecution execution = new TaskExecution(task.getId());
        executionRegistry.register(execution);

        long effectiveTimeout = task.getTimeoutSeconds() > 0
                ? task.getTimeoutSeconds()
                : defaultTimeoutSeconds;

        Future<?> future = threadPool.submit(() -> executeAsync(execution, task));
        execution.setFuture(future);

        if (execution.isCancellationRequested()) {
            future.cancel(true);
        }

        if (effectiveTimeout > 0) {
            scheduleTimeout(execution, effectiveTimeout);
        }

        log.info("Task {} submitted for async execution, executionId={}", task.getId(), execution.getExecutionId());
        return execution.getExecutionId();
    }

    private void executeAsync(TaskExecution execution, Task task) {
        if (!execution.start()) {
            log.debug("Execution {} was cancelled before starting", execution.getExecutionId());
            return;
        }

        log.debug("Execution {} started", execution.getExecutionId());
        TaskExecutionDetails details = syncExecutor.executeTask(task);

        if (execution.isCancellationRequested()) {
            execution.markCancelled();
            log.info("Execution {} marked as {}", execution.getExecutionId(), execution.getState());
        } else {
            execution.complete(details);
            log.info("Execution {} completed with status {}", execution.getExecutionId(), execution.getState());
        }
    }

    private void scheduleTimeout(TaskExecution execution, long timeoutSeconds) {
        timeoutScheduler.schedule(() -> {
            boolean requested = execution.requestCancellation(true);
            if (requested) {
                execution.cancelFuture();
                log.info("Execution {} timed out after {}s", execution.getExecutionId(), timeoutSeconds);
            }
        }, timeoutSeconds, TimeUnit.SECONDS);
    }

    private static ThreadFactory daemonFactory(String namePrefix) {
        AtomicLong counter = new AtomicLong();
        return r -> {
            Thread thread = new Thread(r, namePrefix + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Shuts down the thread pool and timeout scheduler, invoked automatically on application context close. */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down AsyncTaskExecutor");
        threadPool.shutdown();
        timeoutScheduler.shutdown();
    }
}
