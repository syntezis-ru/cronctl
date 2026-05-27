package ru.syntezis.cronctl.core.async;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import ru.syntezis.cronctl.core.sync.BlockingTaskExecutor;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;
import ru.syntezis.cronctl.properties.CronctlProperties;
import ru.syntezis.cronctl.util.ScheduleUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class AsyncTaskExecutorTest {

    private final BlockingTaskExecutor syncExecutor = new BlockingTaskExecutor();
    private final ExecutionRegistry executionRegistry = new ExecutionRegistry();
    private AsyncTaskExecutor underTest;

    @BeforeEach
    void setUp() {
        underTest = new AsyncTaskExecutor(syncExecutor, executionRegistry,
                new CronctlProperties.Executor(2, 10, 0));
    }

    @AfterEach
    void tearDown() {
        underTest.shutdown();
    }

    @Test
    void submit_ValidTask_ReturnsExecutionIdAndExecutionIsRegistered() throws Exception {
        // Given
        Task task = buildTask(new InstantScheduler(), "fastTask", 0);

        // When
        final UUID executionId = underTest.submit(task);

        // Then
        assertThat(executionId).isNotNull();
        assertThat(executionRegistry.getById(executionId)).isPresent();
    }

    @Test
    void submit_ValidTask_ExecutionEventuallySucceeds() throws Exception {
        // Given
        Task task = buildTask(new InstantScheduler(), "fastTask", 0);

        // When
        UUID executionId = underTest.submit(task);
        TaskExecution execution = executionRegistry.getById(executionId).orElseThrow();

        // Then
        await().atMost(2, SECONDS).until(execution::isTerminal);
        assertThat(execution.getState()).isEqualTo(TaskExecutionStatus.SUCCEEDED);
    }

    @Test
    void submit_ThrowingTask_ExecutionEventuallyFails() throws Exception {
        // Given
        Task task = buildTask(new ThrowingScheduler(), "throwingTask", 0);

        // When
        UUID executionId = underTest.submit(task);
        TaskExecution execution = executionRegistry.getById(executionId).orElseThrow();

        // Then
        await().atMost(2, SECONDS).until(execution::isTerminal);
        assertThat(execution.getState()).isEqualTo(TaskExecutionStatus.FAILED);
    }

    @Test
    void submit_WithTaskLevelTimeout_ExecutionTimesOut() throws Exception {
        // Given — task-level timeout of 1 s, no global timeout
        Task task = buildTask(new InterruptibleBlockingScheduler(), "blockingTask", 1);

        // When
        UUID executionId = underTest.submit(task);
        TaskExecution execution = executionRegistry.getById(executionId).orElseThrow();

        // Then
        await().atMost(3, SECONDS).until(execution::isTerminal);
        assertThat(execution.getState()).isEqualTo(TaskExecutionStatus.TIMED_OUT);
    }

    @Test
    void cancel_RunningExecution_ExecutionCancelled() throws Exception {
        // Given
        Task task = buildTask(new InterruptibleBlockingScheduler(), "blockingTask", 0);
        UUID executionId = underTest.submit(task);
        TaskExecution execution = executionRegistry.getById(executionId).orElseThrow();

        await().atMost(2, SECONDS)
                .until(() -> execution.getState() == TaskExecutionStatus.RUNNING);

        // When
        executionRegistry.cancel(executionId);

        // Then
        await().atMost(2, SECONDS).until(execution::isTerminal);
        assertThat(execution.getState()).isEqualTo(TaskExecutionStatus.CANCELLED);
    }

    @Test
    void submit_QueueFull_ThrowsRejectedExecutionException() throws Exception {
        // Given — pool=1, queue=1 → max 2 inflight before rejection
        CountDownLatch blockLatch = new CountDownLatch(1);
        AsyncTaskExecutor smallExecutor = new AsyncTaskExecutor(syncExecutor, new ExecutionRegistry(),
                new CronctlProperties.Executor(1, 1, 0));

        Task blockingTask = buildTask(new LatchBlockingScheduler(blockLatch), "latchTask", 0);

        try {
            smallExecutor.submit(blockingTask); // → worker thread
            smallExecutor.submit(blockingTask); // → queue

            // When
            ThrowingCallable invoke = () -> smallExecutor.submit(blockingTask);

            // Then
            assertThatThrownBy(invoke).isInstanceOf(RejectedExecutionException.class);
        } finally {
            blockLatch.countDown();
            smallExecutor.shutdown();
        }
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
                        .id(UUID.randomUUID())
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
        public void fastTask() { }

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

    private static class LatchBlockingScheduler {

        private final CountDownLatch latch;

        LatchBlockingScheduler(CountDownLatch latch) {
            this.latch = latch;
        }

        @Scheduled(fixedRate = Long.MAX_VALUE)
        public void latchTask() throws InterruptedException {
            latch.await();
        }
    }
}
