package ru.syntezis.cronctl.core.async;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.core.execution.DisabledRetryLifecycleHandler;
import ru.syntezis.cronctl.core.execution.ExecutionLifecycleService;
import ru.syntezis.cronctl.core.execution.ExecutionStore;
import ru.syntezis.cronctl.core.execution.InMemoryExecutionStore;
import ru.syntezis.cronctl.core.execution.TaskConcurrencyController;
import ru.syntezis.cronctl.core.sync.BlockingTaskExecutor;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.ConcurrencyPolicy;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;
import ru.syntezis.cronctl.properties.CronctlProperties;
import ru.syntezis.cronctl.util.ScheduleUtils;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AsyncTaskExecutorTest {

    private ExecutionStore executionStore;
    private ExecutionLifecycleService lifecycleService;
    private AsyncTaskExecutor underTest;

    @BeforeEach
    void setUp() {
        CronctlProperties.History history = new CronctlProperties.History();
        executionStore = new InMemoryExecutionStore(history);
        lifecycleService = new ExecutionLifecycleService(
                executionStore, new BlockingTaskExecutor(), new TaskConcurrencyController(),
                new DisabledRetryLifecycleHandler(),
                Clock.systemUTC(), "test-node"
        );
        underTest = new AsyncTaskExecutor(lifecycleService, new CronctlProperties.Executor(2, 10, 0));
    }

    @AfterEach
    void tearDown() {
        underTest.shutdown();
    }

    @Test
    void submit_ValidTask_ExecutionRegisteredAndEventuallySucceeded() throws Exception {
        // Given
        Task task = buildTask(new InstantScheduler(), "fastTask", 0);

        // When
        final TaskExecution actual = underTest.submit(task);

        // Then
        await().atMost(2, SECONDS).until(actual::isTerminal);
        assertThat(executionStore.findById(actual.getExecutionId())).contains(actual);
        assertThat(actual.getStatus()).isEqualTo(TaskExecutionStatus.SUCCEEDED);
    }

    @Test
    void submit_ThrowingTask_ExecutionEventuallyFailed() throws Exception {
        // Given
        Task task = buildTask(new ThrowingScheduler(), "throwingTask", 0);

        // When
        final TaskExecution actual = underTest.submit(task);

        // Then
        await().atMost(2, SECONDS).until(actual::isTerminal);
        assertThat(actual.getStatus()).isEqualTo(TaskExecutionStatus.FAILED);
        assertThat(actual.getErrorMessage()).isEqualTo("task error");
    }

    @Test
    void submit_TaskLevelTimeout_ExecutionTimedOut() throws Exception {
        // Given
        Task task = buildTask(new InterruptibleBlockingScheduler(), "blockingTask", 1);

        // When
        final TaskExecution actual = underTest.submit(task);

        // Then
        await().atMost(3, SECONDS).until(actual::isTerminal);
        assertThat(actual.getStatus()).isEqualTo(TaskExecutionStatus.TIMED_OUT);
        assertThat(actual.getErrorMessage()).isNull();
    }

    @Test
    void submit_InheritedGlobalTimeout_ExecutionTimedOut() throws Exception {
        // Given
        underTest.shutdown();
        underTest = new AsyncTaskExecutor(lifecycleService, new CronctlProperties.Executor(2, 10, 1));
        Task task = buildTask(new InterruptibleBlockingScheduler(), "blockingTask",
                CronctlTask.USE_GLOBAL_TIMEOUT);

        // When
        final TaskExecution actual = underTest.submit(task);

        // Then
        await().atMost(3, SECONDS).until(actual::isTerminal);
        assertThat(actual.getStatus()).isEqualTo(TaskExecutionStatus.TIMED_OUT);
    }

    @Test
    void submit_NoTimeoutOverride_ExecutionKeepsRunning() throws Exception {
        // Given
        underTest.shutdown();
        underTest = new AsyncTaskExecutor(lifecycleService, new CronctlProperties.Executor(2, 10, 1));
        CountDownLatch blockLatch = new CountDownLatch(1);
        Task task = buildTask(new LatchBlockingScheduler(blockLatch), "latchTask", CronctlTask.NO_TIMEOUT);
        TaskExecution execution = underTest.submit(task);

        try {
            await().atMost(2, SECONDS)
                    .until(() -> execution.getStatus() == TaskExecutionStatus.RUNNING);

            // When
            await().during(1200, MILLISECONDS).atMost(2, SECONDS)
                    .until(() -> execution.getStatus() == TaskExecutionStatus.RUNNING);
            final TaskExecutionStatus actual = execution.getStatus();

            // Then
            final TaskExecutionStatus expected = TaskExecutionStatus.RUNNING;
            assertThat(actual).isEqualTo(expected);
        } finally {
            blockLatch.countDown();
        }
    }

    @Test
    void requestCancellation_RunningExecution_ExecutionCancelled() throws Exception {
        // Given
        Task task = buildTask(new InterruptibleBlockingScheduler(), "blockingTask", 0);
        TaskExecution execution = underTest.submit(task);
        await().atMost(2, SECONDS)
                .until(() -> execution.getStatus() == TaskExecutionStatus.RUNNING);

        // When
        lifecycleService.requestCancellation(execution, false);

        // Then
        await().atMost(2, SECONDS).until(execution::isTerminal);
        assertThat(execution.getStatus()).isEqualTo(TaskExecutionStatus.CANCELLED);
    }

    @Test
    void submit_FullQueue_ExecutionSkipped() throws Exception {
        // Given
        CountDownLatch blockLatch = new CountDownLatch(1);
        AsyncTaskExecutor smallExecutor = new AsyncTaskExecutor(
                lifecycleService, new CronctlProperties.Executor(1, 1, 0)
        );
        Task task = buildTask(new LatchBlockingScheduler(blockLatch), "latchTask", 0);

        try {
            smallExecutor.submit(task);
            smallExecutor.submit(task);

            // When
            final TaskExecution actual = smallExecutor.submit(task);

            // Then
            assertThat(actual.getStatus()).isEqualTo(TaskExecutionStatus.SKIPPED);
            assertThat(actual.getStatusReason()).isEqualTo("QUEUE_REJECTED");
        } finally {
            blockLatch.countDown();
            smallExecutor.shutdown();
        }
    }

    @Test
    void submit_SkipPolicyAtLimit_ConcurrentExecutionSkipped() throws Exception {
        // Given
        CountDownLatch blockLatch = new CountDownLatch(1);
        Task task = buildTask(new LatchBlockingScheduler(blockLatch), "latchTask", 0);
        task.setConcurrencyPolicy(ConcurrencyPolicy.SKIP);
        task.setMaxConcurrentExecutions(1);
        TaskExecution firstExecution = underTest.submit(task);
        await().atMost(2, SECONDS)
                .until(() -> firstExecution.getStatus() == TaskExecutionStatus.RUNNING);

        try {
            // When
            final TaskExecution actual = underTest.submit(task);
            await().atMost(2, SECONDS).until(actual::isTerminal);

            // Then
            assertThat(actual.getStatus()).isEqualTo(TaskExecutionStatus.SKIPPED);
            assertThat(actual.getStatusReason()).isEqualTo("CONCURRENT_EXECUTION");
        } finally {
            blockLatch.countDown();
        }
    }

    @Test
    void submit_QueuePolicyAtLimit_ConcurrentExecutionWaitsThenSucceeds() throws Exception {
        // Given
        CountDownLatch blockLatch = new CountDownLatch(1);
        Task task = buildTask(new LatchBlockingScheduler(blockLatch), "latchTask", 0);
        task.setConcurrencyPolicy(ConcurrencyPolicy.QUEUE);
        task.setMaxConcurrentExecutions(1);
        TaskExecution firstExecution = underTest.submit(task);
        await().atMost(2, SECONDS)
                .until(() -> firstExecution.getStatus() == TaskExecutionStatus.RUNNING);
        TaskExecution queuedExecution = underTest.submit(task);

        try {
            await().during(200, MILLISECONDS).atMost(1, SECONDS)
                    .until(() -> queuedExecution.getStatus() == TaskExecutionStatus.QUEUED);

            // When
            blockLatch.countDown();
            await().atMost(2, SECONDS).until(firstExecution::isTerminal);
            await().atMost(2, SECONDS).until(queuedExecution::isTerminal);
            final TaskExecutionStatus actual = queuedExecution.getStatus();

            // Then
            final TaskExecutionStatus expected = TaskExecutionStatus.SUCCEEDED;
            assertThat(actual).isEqualTo(expected);
        } finally {
            blockLatch.countDown();
        }
    }

    @Test
    void submit_CancelPreviousPolicyAtLimit_OldestExecutionCancelledAndReplacementSucceeded() throws Exception {
        // Given
        FirstInvocationBlockingScheduler scheduler = new FirstInvocationBlockingScheduler();
        Task task = buildTask(scheduler, "run", 0);
        task.setConcurrencyPolicy(ConcurrencyPolicy.CANCEL_PREVIOUS);
        task.setMaxConcurrentExecutions(1);
        TaskExecution firstExecution = underTest.submit(task);
        await().atMost(2, SECONDS)
                .until(() -> firstExecution.getStatus() == TaskExecutionStatus.RUNNING);

        // When
        final TaskExecution actual = underTest.submit(task);
        await().atMost(2, SECONDS).until(firstExecution::isTerminal);
        await().atMost(2, SECONDS).until(actual::isTerminal);

        // Then
        assertThat(firstExecution.getStatus()).isEqualTo(TaskExecutionStatus.CANCELLED);
        assertThat(firstExecution.getStatusReason()).isEqualTo("CANCELLED_BY_CONCURRENT_EXECUTION");
        assertThat(actual.getStatus()).isEqualTo(TaskExecutionStatus.SUCCEEDED);
    }

    private Task buildTask(Object bean, String methodName, long timeoutSeconds) throws NoSuchMethodException {
        Method method = bean.getClass().getDeclaredMethod(methodName);
        return Task.builder()
                .label(methodName)
                .description(methodName)
                .group("test")
                .tags(List.of())
                .timeoutSeconds(timeoutSeconds)
                .details(ScheduledMethodDetails.builder()
                        .taskKey("test." + methodName)
                        .methodName(methodName)
                        .schedule(ScheduleUtils.assembleScheduleDetails(method.getAnnotation(Scheduled.class)))
                        .build())
                .reference(ScheduledMethodReference.builder()
                        .beanName("testBean")
                        .bean(bean)
                        .method(method)
                        .build())
                .build();
    }

    private static class InstantScheduler {

        @Scheduled(fixedRate = Long.MAX_VALUE)
        public void fastTask() {
        }

    }

    private static class ThrowingScheduler {

        @Scheduled(fixedRate = Long.MAX_VALUE)
        public void throwingTask() {
            throw new RuntimeException("task error");
        }

    }

    private static class InterruptibleBlockingScheduler {

        @Scheduled(fixedRate = Long.MAX_VALUE)
        public void blockingTask() throws InterruptedException {
            Thread.sleep(Long.MAX_VALUE);
        }

    }

    private static class FirstInvocationBlockingScheduler {

        private final AtomicInteger invocationCount = new AtomicInteger();

        @Scheduled(fixedRate = Long.MAX_VALUE)
        public void run() throws InterruptedException {
            if (invocationCount.incrementAndGet() == 1) {
                Thread.sleep(Long.MAX_VALUE);
            }
        }

    }

    @RequiredArgsConstructor
    private static class LatchBlockingScheduler {

        private final CountDownLatch latch;

        @Scheduled(fixedRate = Long.MAX_VALUE)
        public void latchTask() throws InterruptedException {
            latch.await();
        }
    }
}
