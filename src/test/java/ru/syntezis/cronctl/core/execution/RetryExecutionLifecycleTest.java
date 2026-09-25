package ru.syntezis.cronctl.core.execution;

import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;
import ru.syntezis.cronctl.core.TaskRegistry;
import ru.syntezis.cronctl.core.async.AsyncTaskExecutor;
import ru.syntezis.cronctl.core.sync.BlockingTaskExecutor;
import ru.syntezis.cronctl.domain.execution.ExecutionQuery;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.RetryPolicy;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.enums.ExecutionSource;
import ru.syntezis.cronctl.enums.RetryTrigger;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;
import ru.syntezis.cronctl.properties.CronctlProperties;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class RetryExecutionLifecycleTest {

    @Test
    void executeSynchronously_RetryableFirstFailure_AutomaticRetrySucceeded() throws NoSuchMethodException {
        // Given
        ExecutionStore executionStore = new InMemoryExecutionStore(new CronctlProperties.History());
        TaskRegistry taskRegistry = new TaskRegistry();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        RetryPolicyEvaluator policyEvaluator = new RetryPolicyEvaluator(() -> 0.5);
        RetryCoordinator retryCoordinator = new RetryCoordinator(
                executionStore,
                taskRegistry,
                policyEvaluator,
                beanFactory.getBeanProvider(ExecutionLifecycleService.class),
                beanFactory.getBeanProvider(AsyncTaskExecutor.class),
                Clock.systemUTC()
        );
        ExecutionLifecycleService underTest = new ExecutionLifecycleService(
                executionStore,
                new BlockingTaskExecutor(),
                new TaskConcurrencyController(),
                retryCoordinator,
                Clock.systemUTC(),
                "test-node"
        );
        AsyncTaskExecutor asyncTaskExecutor = new AsyncTaskExecutor(
                underTest, new CronctlProperties.Executor(1, 10, 0)
        );
        beanFactory.addBean("executionLifecycleService", underTest);
        beanFactory.addBean("asyncTaskExecutor", asyncTaskExecutor);
        FailOnceTask bean = new FailOnceTask();
        Method method = FailOnceTask.class.getDeclaredMethod("run");
        Task task = task(bean, method);
        taskRegistry.add(task.getTaskKey(), task);

        try {
            // When
            TaskExecution firstExecution = underTest.executeSynchronously(task);
            await().atMost(3, SECONDS).until(() -> executionStore.findAll(ExecutionQuery.builder()
                            .source(ExecutionSource.RETRY)
                            .build())
                    .getExecutions().stream().anyMatch(TaskExecution::isTerminal));
            final TaskExecution actual = retryExecution(executionStore);

            // Then
            final TaskExecutionStatus expected = TaskExecutionStatus.SUCCEEDED;
            assertThat(firstExecution.getStatus()).isEqualTo(TaskExecutionStatus.FAILED);
            assertThat(actual.getStatus()).isEqualTo(expected);
            assertThat(actual.getSource()).isEqualTo(ExecutionSource.RETRY);
            assertThat(actual.getRetryTrigger()).isEqualTo(RetryTrigger.AUTOMATIC);
            assertThat(actual.getParentExecutionId()).isEqualTo(firstExecution.getExecutionId());
            assertThat(actual.getAttempt()).isEqualTo(2);
            assertThat(bean.getInvocations()).isEqualTo(2);
        } finally {
            retryCoordinator.shutdown();
            asyncTaskExecutor.shutdown();
        }
    }

    @Test
    void onApplicationEvent_PersistedQueuedRetry_RetryRestoredAndSucceeded() throws NoSuchMethodException {
        // Given
        ExecutionStore executionStore = new InMemoryExecutionStore(new CronctlProperties.History());
        TaskRegistry taskRegistry = new TaskRegistry();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        RetryPolicyEvaluator policyEvaluator = new RetryPolicyEvaluator(() -> 0.5);
        RetryCoordinator underTest = new RetryCoordinator(
                executionStore,
                taskRegistry,
                policyEvaluator,
                beanFactory.getBeanProvider(ExecutionLifecycleService.class),
                beanFactory.getBeanProvider(AsyncTaskExecutor.class),
                Clock.systemUTC()
        );
        ExecutionLifecycleService lifecycleService = new ExecutionLifecycleService(
                executionStore,
                new BlockingTaskExecutor(),
                new TaskConcurrencyController(),
                underTest,
                Clock.systemUTC(),
                "test-node"
        );
        AsyncTaskExecutor asyncTaskExecutor = new AsyncTaskExecutor(
                lifecycleService, new CronctlProperties.Executor(1, 10, 0)
        );
        beanFactory.addBean("executionLifecycleService", lifecycleService);
        beanFactory.addBean("asyncTaskExecutor", asyncTaskExecutor);
        SuccessfulTask bean = new SuccessfulTask();
        Method method = SuccessfulTask.class.getDeclaredMethod("run");
        Task task = task(bean, method, RetryPolicy.disabled());
        taskRegistry.add(task.getTaskKey(), task);
        TaskExecution parent = lifecycleService.createQueued(
                task.getTaskKey(), ExecutionSource.MANUAL_SYNC, null
        );
        TaskExecution persistedRetry = lifecycleService.createRetryQueued(
                parent, Instant.now(), RetryTrigger.AUTOMATIC
        );
        GenericApplicationContext applicationContext = new GenericApplicationContext();

        try {
            // When
            underTest.onApplicationEvent(new ContextRefreshedEvent(applicationContext));
            await().atMost(3, SECONDS).until(persistedRetry::isTerminal);
            final TaskExecutionStatus actual = persistedRetry.getStatus();

            // Then
            final TaskExecutionStatus expected = TaskExecutionStatus.SUCCEEDED;
            assertThat(actual).isEqualTo(expected);
            assertThat(bean.getInvocations()).isEqualTo(1);
        } finally {
            applicationContext.close();
            underTest.shutdown();
            asyncTaskExecutor.shutdown();
        }
    }

    @Test
    void onApplicationEvent_MultiplePagesOfRetriesForMissingTask_AllRetriesSkipped() {
        // Given
        ExecutionStore executionStore = new InMemoryExecutionStore(new CronctlProperties.History());
        TaskRegistry taskRegistry = new TaskRegistry();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        RetryCoordinator underTest = new RetryCoordinator(
                executionStore,
                taskRegistry,
                new RetryPolicyEvaluator(() -> 0.5),
                beanFactory.getBeanProvider(ExecutionLifecycleService.class),
                beanFactory.getBeanProvider(AsyncTaskExecutor.class),
                Clock.systemUTC()
        );
        ExecutionLifecycleService lifecycleService = new ExecutionLifecycleService(
                executionStore,
                new BlockingTaskExecutor(),
                new TaskConcurrencyController(),
                underTest,
                Clock.systemUTC(),
                "test-node"
        );
        beanFactory.addBean("executionLifecycleService", lifecycleService);
        TaskExecution parent = lifecycleService.createQueued("missing.retry.task", ExecutionSource.MANUAL_SYNC, null);
        final int expected = 201;
        for (int i = 0; i < expected; i++) {
            lifecycleService.createRetryQueued(parent, Instant.now(), RetryTrigger.AUTOMATIC);
        }
        GenericApplicationContext applicationContext = new GenericApplicationContext();

        try {
            // When
            underTest.onApplicationEvent(new ContextRefreshedEvent(applicationContext));
            final long actual = executionStore.findAll(ExecutionQuery.builder()
                            .source(ExecutionSource.RETRY)
                            .status(TaskExecutionStatus.SKIPPED)
                            .size(expected)
                            .build())
                    .getTotal();

            // Then
            assertThat(actual).isEqualTo(expected);
        } finally {
            applicationContext.close();
            underTest.shutdown();
        }
    }

    private Task task(FailOnceTask bean, Method method) {
        return task(bean, method, RetryPolicy.builder()
                .retries(1)
                .delay(Duration.ZERO)
                .maxDelay(Duration.ofSeconds(1))
                .build());
    }

    private Task task(Object bean, Method method, RetryPolicy retryPolicy) {
        return Task.builder()
                .label("Retry test")
                .enabled(true)
                .retryPolicy(retryPolicy)
                .details(ScheduledMethodDetails.builder()
                        .taskKey("test.retry")
                        .methodName(method.getName())
                        .build())
                .reference(ScheduledMethodReference.builder()
                        .beanName("failOnceTask")
                        .bean(bean)
                        .method(method)
                        .build())
                .build();
    }

    private TaskExecution retryExecution(ExecutionStore executionStore) {
        List<TaskExecution> retries = executionStore.findAll(ExecutionQuery.builder()
                        .source(ExecutionSource.RETRY)
                        .build())
                .getExecutions();
        assertThat(retries).hasSize(1);
        return retries.get(0);
    }

    @NoArgsConstructor
    static class FailOnceTask {

        private final AtomicInteger invocations = new AtomicInteger();

        public void run() {
            if (invocations.incrementAndGet() == 1) {
                throw new IllegalStateException("temporary failure");
            }
        }

        private int getInvocations() {
            return invocations.get();
        }

    }

    @NoArgsConstructor
    static class SuccessfulTask {

        private final AtomicInteger invocations = new AtomicInteger();

        public void run() {
            invocations.incrementAndGet();
        }

        private int getInvocations() {
            return invocations.get();
        }

    }

}
