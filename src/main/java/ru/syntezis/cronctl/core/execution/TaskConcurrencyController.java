package ru.syntezis.cronctl.core.execution;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.ConcurrencyPolicy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/** Coordinates task concurrency within the current application process. */
@Slf4j
public class TaskConcurrencyController {

    private final Map<String, TaskConcurrencyState> taskStates = new ConcurrentHashMap<>();

    /**
     * Attempts to admit an execution according to the task's configured policy.
     *
     * @return {@code true} when the caller owns a permit, or {@code false} when SKIP rejects it
     */
    public boolean acquire(Task task, TaskExecution execution,
                           Consumer<TaskExecution> previousExecutionCanceller) throws InterruptedException {
        ConcurrencyPolicy policy = task.getConcurrencyPolicy();
        if (policy == ConcurrencyPolicy.ALLOW) {
            return true;
        }

        TaskConcurrencyState state = taskStates.computeIfAbsent(task.getTaskKey(), ignoredTaskKey ->
                new TaskConcurrencyState(task.getMaxConcurrentExecutions()));
        boolean acquired;
        if (policy == ConcurrencyPolicy.SKIP) {
            acquired = state.tryAcquire();
        } else if (policy == ConcurrencyPolicy.CANCEL_PREVIOUS) {
            acquired = state.tryAcquire();
            if (!acquired) {
                state.cancelOldest(previousExecutionCanceller);
                state.acquire();
                acquired = true;
            }
        } else {
            state.acquire();
            acquired = true;
        }

        if (acquired) {
            state.register(execution, Thread.currentThread());
        }
        return acquired;
    }

    /** Releases a permit previously acquired for the execution. */
    public void release(Task task, TaskExecution execution) {
        if (task.getConcurrencyPolicy() == ConcurrencyPolicy.ALLOW) {
            return;
        }

        TaskConcurrencyState state = taskStates.get(task.getTaskKey());
        if (state != null) {
            state.release(execution.getExecutionId());
        }
    }

    private static class TaskConcurrencyState {

        private final Semaphore semaphore;
        private final Lock activeExecutionsLock = new ReentrantLock();
        private final Map<UUID, ActiveExecution> activeExecutions = new LinkedHashMap<>();

        private TaskConcurrencyState(int maxConcurrentExecutions) {
            this.semaphore = new Semaphore(maxConcurrentExecutions, true);
        }

        private boolean tryAcquire() {
            return semaphore.tryAcquire();
        }

        private void acquire() throws InterruptedException {
            semaphore.acquire();
        }

        private void register(TaskExecution execution, Thread thread) {
            activeExecutionsLock.lock();
            try {
                activeExecutions.put(execution.getExecutionId(), new ActiveExecution(execution, thread));
            } finally {
                activeExecutionsLock.unlock();
            }
        }

        private void cancelOldest(Consumer<TaskExecution> previousExecutionCanceller) {
            activeExecutionsLock.lock();
            try {
                activeExecutions.values().stream()
                        .filter(activeExecution -> !activeExecution.getExecution().isCancellationRequested())
                        .findFirst()
                        .ifPresent(activeExecution -> {
                            previousExecutionCanceller.accept(activeExecution.getExecution());
                            activeExecution.getThread().interrupt();
                            log.info("Requested cancellation of execution {} to admit a replacement",
                                    activeExecution.getExecution().getExecutionId());
                        });
            } finally {
                activeExecutionsLock.unlock();
            }
        }

        private void release(UUID executionId) {
            activeExecutionsLock.lock();
            try {
                if (activeExecutions.remove(executionId) != null) {
                    semaphore.release();
                }
            } finally {
                activeExecutionsLock.unlock();
            }
        }
    }

    @AllArgsConstructor
    @Getter
    private static class ActiveExecution {

        private final TaskExecution execution;
        private final Thread thread;

    }

}
