package ru.syntezis.cronctl.web;

import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.annotation.DirtiesContext;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.core.execution.ExecutionStore;
import ru.syntezis.cronctl.domain.execution.ExecutionPage;
import ru.syntezis.cronctl.domain.execution.ExecutionQuery;
import ru.syntezis.cronctl.domain.execution.ScheduledTaskHealth;
import ru.syntezis.cronctl.enums.ExecutionSource;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.util.concurrent.CountDownLatch;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "cronctl.history.node-id=history-test-node")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ScheduledExecutionHistoryTest {

    @Autowired
    private Cronctl cronctl;

    @Autowired
    private ExecutionStore executionStore;

    @Autowired
    private HistoryScheduler historyScheduler;

    @Test
    void scheduledInvocation_RegisteredTask_SucceededExecutionRecorded() throws InterruptedException {
        // Given
        assertThat(historyScheduler.getInvocationLatch().await(3, SECONDS)).isTrue();
        String taskKey = cronctl.getAllTasks().stream()
                .filter(task -> "Automatic history probe".equals(task.getLabel()))
                .findFirst()
                .orElseThrow()
                .getTaskKey();
        ExecutionQuery query = ExecutionQuery.builder()
                .taskKey(taskKey)
                .source(ExecutionSource.SCHEDULED)
                .status(TaskExecutionStatus.SUCCEEDED)
                .build();

        // When
        await().atMost(3, SECONDS)
                .until(() -> executionStore.findAll(query).getTotal() > 0);
        final ExecutionPage actual = executionStore.findAll(query);

        // Then
        assertThat(actual.getExecutions()).isNotEmpty().allSatisfy(execution -> {
            assertThat(execution.getNodeId()).isEqualTo("history-test-node");
            assertThat(execution.getSource()).isEqualTo(ExecutionSource.SCHEDULED);
            assertThat(execution.getStatus()).isEqualTo(TaskExecutionStatus.SUCCEEDED);
        });
    }

    @Test
    void scheduledInvocation_FailingTask_FailureAndHealthRecorded() throws InterruptedException {
        // Given
        assertThat(historyScheduler.getFailureLatch().await(3, SECONDS)).isTrue();
        String taskKey = cronctl.getAllTasks().stream()
                .filter(task -> "Automatic history failure probe".equals(task.getLabel()))
                .findFirst()
                .orElseThrow()
                .getTaskKey();
        ExecutionQuery query = ExecutionQuery.builder()
                .taskKey(taskKey)
                .source(ExecutionSource.SCHEDULED)
                .status(TaskExecutionStatus.FAILED)
                .build();

        // When
        await().atMost(3, SECONDS)
                .until(() -> executionStore.findAll(query).getTotal() > 0);
        final ExecutionPage actual = executionStore.findAll(query);
        final ScheduledTaskHealth health = executionStore.getScheduledHealth(taskKey);

        // Then
        assertThat(actual.getExecutions()).singleElement().satisfies(execution -> {
            assertThat(execution.getErrorType()).isEqualTo(IllegalStateException.class.getName());
            assertThat(execution.getErrorMessage()).isEqualTo("scheduled failure");
        });
        assertThat(health.getLastExecutionStatus()).isEqualTo(TaskExecutionStatus.FAILED);
        assertThat(health.getConsecutiveFailures()).isEqualTo(1);
    }

    @TestConfiguration
    static class HistoryTestConfiguration {

        @Bean
        HistoryScheduler historyScheduler() {
            return new HistoryScheduler();
        }

    }

    @Getter
    static class HistoryScheduler {

        private final CountDownLatch invocationLatch = new CountDownLatch(1);
        private final CountDownLatch failureLatch = new CountDownLatch(1);

        @CronctlTask(label = "Automatic history probe")
        @Scheduled(initialDelay = 50L, fixedDelay = 3_600_000L)
        public void trackedTask() {
            invocationLatch.countDown();
        }

        @CronctlTask(label = "Automatic history failure probe")
        @Scheduled(initialDelay = 75L, fixedDelay = 3_600_000L)
        public void failingTask() {
            failureLatch.countDown();
            throw new IllegalStateException("scheduled failure");
        }

    }

}
