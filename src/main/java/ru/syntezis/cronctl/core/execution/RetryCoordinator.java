package ru.syntezis.cronctl.core.execution;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import ru.syntezis.cronctl.core.TaskRegistry;
import ru.syntezis.cronctl.core.async.AsyncTaskExecutor;
import ru.syntezis.cronctl.domain.execution.ExecutionPage;
import ru.syntezis.cronctl.domain.execution.ExecutionQuery;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.task.RetryPolicy;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.ExecutionSource;
import ru.syntezis.cronctl.enums.RetryTrigger;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Schedules automatic and manual retry executions without blocking timer threads. */
@Slf4j
@RequiredArgsConstructor
public class RetryCoordinator implements ApplicationListener<ContextRefreshedEvent>, RetryLifecycleHandler {

    private static final int RESTORE_PAGE_SIZE = 200;

    private final ExecutionStore executionStore;
    private final TaskRegistry taskRegistry;
    private final RetryPolicyEvaluator policyEvaluator;
    private final ObjectProvider<ExecutionLifecycleService> lifecycleServiceProvider;
    private final ObjectProvider<AsyncTaskExecutor> asyncTaskExecutorProvider;
    private final Clock clock;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            daemonFactory("cronctl-retry-")
    );
    private final Map<UUID, ScheduledFuture<?>> pendingRetries = new ConcurrentHashMap<>();
    private final AtomicBoolean restored = new AtomicBoolean();

    @Override
    public void scheduleAutomatic(Task task, TaskExecution failedExecution, Throwable failure) {
        RetryPolicy policy = task.getRetryPolicy();
        if (failedExecution.getAttempt() > policy.getRetries() || !policyEvaluator.isRetryable(policy, failure)) {
            return;
        }

        Duration delay = policyEvaluator.calculateDelay(policy, failedExecution.getAttempt());
        Instant plannedAt = clock.instant().plus(delay);
        TaskExecution retry = lifecycleServiceProvider.getObject().createRetryQueued(
                failedExecution, plannedAt, RetryTrigger.AUTOMATIC
        );
        schedule(task, retry);
        log.info("Scheduled automatic retry {} for task {} at {} (attempt {})",
                retry.getExecutionId(), task.getTaskKey(), plannedAt, retry.getAttempt());
    }

    public TaskExecution scheduleManual(Task task, TaskExecution parentExecution) {
        Instant plannedAt = clock.instant();
        TaskExecution retry = lifecycleServiceProvider.getObject().createRetryQueued(
                parentExecution, plannedAt, RetryTrigger.MANUAL
        );
        schedule(task, retry);
        log.info("Scheduled manual retry {} for task {}", retry.getExecutionId(), task.getTaskKey());
        return retry;
    }

    @Override
    public void cancelPending(TaskExecution execution) {
        ScheduledFuture<?> pending = pendingRetries.remove(execution.getExecutionId());
        if (pending != null) {
            pending.cancel(true);
        }
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!restored.compareAndSet(false, true)) {
            return;
        }

        int pageNumber = 0;
        ExecutionPage page;
        List<TaskExecution> queuedRetries = new ArrayList<>();
        do {
            page = executionStore.findAll(ExecutionQuery.builder()
                    .source(ExecutionSource.RETRY)
                    .status(TaskExecutionStatus.QUEUED)
                    .page(pageNumber++)
                    .size(RESTORE_PAGE_SIZE)
                    .build());
            queuedRetries.addAll(page.getExecutions());
        } while (page.hasNext());
        queuedRetries.forEach(this::restore);
    }

    private void restore(TaskExecution execution) {
        taskRegistry.getById(execution.getTaskKey()).ifPresentOrElse(
                task -> schedule(task, execution),
                () -> lifecycleServiceProvider.getObject().skip(execution, "TASK_NOT_REGISTERED")
        );
    }

    private void schedule(Task task, TaskExecution execution) {
        long delayMillis = Math.max(0L, Duration.between(clock.instant(), execution.getPlannedAt()).toMillis());
        ScheduledFuture<?> future = scheduler.schedule(
                () -> submit(task, execution), delayMillis, TimeUnit.MILLISECONDS
        );
        pendingRetries.put(execution.getExecutionId(), future);
        if (future.isDone()) {
            pendingRetries.remove(execution.getExecutionId(), future);
        }
        if (execution.isCancellationRequested()) {
            cancelPending(execution);
        }
    }

    private void submit(Task task, TaskExecution execution) {
        pendingRetries.remove(execution.getExecutionId());
        if (execution.isTerminal()) {
            return;
        }
        asyncTaskExecutorProvider.getObject().submit(task, execution);
    }

    private static ThreadFactory daemonFactory(String namePrefix) {
        AtomicLong counter = new AtomicLong();
        return runnable -> {
            Thread thread = new Thread(runnable, namePrefix + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        pendingRetries.clear();
    }

}
