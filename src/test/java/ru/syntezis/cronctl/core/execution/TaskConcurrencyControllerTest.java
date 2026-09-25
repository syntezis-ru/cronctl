package ru.syntezis.cronctl.core.execution;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.ConcurrencyPolicy;
import ru.syntezis.cronctl.enums.ExecutionSource;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

class TaskConcurrencyControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");

    private final TaskConcurrencyController underTest = new TaskConcurrencyController();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void acquire_AllowPolicy_AllExecutionsAdmitted() throws InterruptedException {
        // Given
        Task task = task("allow.task", ConcurrencyPolicy.ALLOW, 1);
        TaskExecution firstExecution = execution(task);
        TaskExecution secondExecution = execution(task);

        // When
        final boolean firstActual = underTest.acquire(task, firstExecution, ignoredExecution -> { });
        final boolean secondActual = underTest.acquire(task, secondExecution, ignoredExecution -> { });

        // Then
        assertThat(firstActual).isTrue();
        assertThat(secondActual).isTrue();
    }

    @Test
    void acquire_SkipPolicyAtLimit_NewExecutionRejected() throws InterruptedException {
        // Given
        Task task = task("skip.task", ConcurrencyPolicy.SKIP, 2);
        TaskExecution firstExecution = execution(task);
        TaskExecution secondExecution = execution(task);
        TaskExecution rejectedExecution = execution(task);
        underTest.acquire(task, firstExecution, ignoredExecution -> { });
        underTest.acquire(task, secondExecution, ignoredExecution -> { });

        // When
        final boolean actual = underTest.acquire(task, rejectedExecution, ignoredExecution -> { });

        // Then
        assertThat(actual).isFalse();
    }

    @Test
    void acquire_QueuePolicyAtLimit_ExecutionWaitsForReleasedPermit() throws Exception {
        // Given
        Task task = task("queue.task", ConcurrencyPolicy.QUEUE, 1);
        TaskExecution firstExecution = execution(task);
        TaskExecution queuedExecution = execution(task);
        underTest.acquire(task, firstExecution, ignoredExecution -> { });
        CountDownLatch acquisitionStarted = new CountDownLatch(1);
        Future<Boolean> queuedAdmission = executor.submit(() -> {
            acquisitionStarted.countDown();
            return underTest.acquire(task, queuedExecution, ignoredExecution -> { });
        });
        assertThat(acquisitionStarted.await(1, SECONDS)).isTrue();
        assertThat(queuedAdmission.isDone()).isFalse();

        // When
        underTest.release(task, firstExecution);
        final boolean actual = queuedAdmission.get(1, SECONDS);

        // Then
        assertThat(actual).isTrue();
        underTest.release(task, queuedExecution);
    }

    @Test
    void acquire_CancelPreviousPolicyAtLimit_OldestExecutionInterruptedBeforeReplacement() throws Exception {
        // Given
        Task task = task("cancel.task", ConcurrencyPolicy.CANCEL_PREVIOUS, 1);
        TaskExecution firstExecution = execution(task);
        TaskExecution replacementExecution = execution(task);
        CountDownLatch firstAcquired = new CountDownLatch(1);
        CountDownLatch firstInterrupted = new CountDownLatch(1);
        Future<?> firstRun = executor.submit(() -> {
            try {
                underTest.acquire(task, firstExecution, ignoredExecution -> { });
                firstAcquired.countDown();
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                firstInterrupted.countDown();
            } finally {
                underTest.release(task, firstExecution);
            }
        });
        assertThat(firstAcquired.await(1, SECONDS)).isTrue();

        // When
        final boolean actual = underTest.acquire(task, replacementExecution, previousExecution ->
                previousExecution.requestCancellation(
                        NOW, false, "CANCELLED_BY_CONCURRENT_EXECUTION"
                ));

        // Then
        assertThat(actual).isTrue();
        assertThat(firstInterrupted.await(1, SECONDS)).isTrue();
        assertThat(firstExecution.isCancellationRequested()).isTrue();
        assertThat(firstExecution.getStatusReason()).isEqualTo("CANCELLED_BY_CONCURRENT_EXECUTION");
        firstRun.get(1, SECONDS);
        underTest.release(task, replacementExecution);
    }

    private Task task(String taskKey, ConcurrencyPolicy policy, int maxConcurrentExecutions) {
        return Task.builder()
                .details(ScheduledMethodDetails.builder()
                        .taskKey(taskKey)
                        .build())
                .concurrencyPolicy(policy)
                .maxConcurrentExecutions(maxConcurrentExecutions)
                .build();
    }

    private TaskExecution execution(Task task) {
        TaskExecution execution = TaskExecution.create(
                task.getTaskKey(), ExecutionSource.MANUAL_ASYNC, "node-1", null, NOW
        );
        execution.queue(NOW);
        return execution;
    }

}
