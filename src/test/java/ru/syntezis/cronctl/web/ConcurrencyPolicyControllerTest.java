package ru.syntezis.cronctl.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.core.execution.ExecutionStore;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.enums.ConcurrencyPolicy;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "cronctl.api.public-access=true")
@AutoConfigureMockMvc
class ConcurrencyPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Cronctl cronctl;

    @Autowired
    private BlockingConcurrencyTask blockingConcurrencyTask;

    @Autowired
    private ExecutionStore executionStore;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @BeforeEach
    void setUp() {
        blockingConcurrencyTask.reset();
    }

    @AfterEach
    void tearDown() {
        blockingConcurrencyTask.release();
        executor.shutdownNow();
    }

    @Test
    void getScheduledTasks_ConcurrencyPolicyConfigured_PolicyFieldsReturned() throws Exception {
        // Given
        String taskKey = taskKey();

        // When
        MvcResult result = mockMvc.perform(get("/api/cronctl/tasks"))
                .andExpect(status().isOk())
                .andReturn();
        List<Map<String, Object>> tasks = JsonPath.read(
                result.getResponse().getContentAsString(), "$.tasks[?(@.task_key == '" + taskKey + "')]"
        );
        final Map<String, Object> actual = tasks.get(0);

        // Then
        assertThat(actual.get("concurrency_policy")).isEqualTo("SKIP");
        assertThat(actual.get("max_concurrent_executions")).isEqualTo(1);
    }

    @Test
    void executeTask_ConcurrentSyncExecutionAtSkipLimit_Returns429WithSkippedHistory() throws Exception {
        // Given
        String taskKey = taskKey();
        Future<MvcResult> firstExecution = executor.submit(() -> mockMvc.perform(
                        post("/api/cronctl/tasks/{taskKey}/execute", taskKey)
                )
                .andExpect(status().isOk())
                .andReturn());
        assertThat(blockingConcurrencyTask.awaitStarted()).isTrue();

        try {
            // When
            final ResultActions actual = mockMvc.perform(
                    post("/api/cronctl/tasks/{taskKey}/execute", taskKey)
            );

            // Then
            actual.andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.status").value("SKIPPED"))
                    .andExpect(jsonPath("$.status_reason").value("CONCURRENT_EXECUTION"));
        } finally {
            blockingConcurrencyTask.release();
            firstExecution.get(2, SECONDS);
        }
    }

    @Test
    void executeTaskAsync_ConcurrentExecutionAtSkipLimit_Returns202AndEventuallySkipped() throws Exception {
        // Given
        String taskKey = taskKey();
        Future<MvcResult> firstExecution = executor.submit(() -> mockMvc.perform(
                        post("/api/cronctl/tasks/{taskKey}/execute", taskKey)
                )
                .andExpect(status().isOk())
                .andReturn());
        assertThat(blockingConcurrencyTask.awaitStarted()).isTrue();

        try {
            // When
            MvcResult result = mockMvc.perform(
                            post("/api/cronctl/tasks/{taskKey}/execute-async", taskKey)
                    )
                    .andExpect(status().isAccepted())
                    .andReturn();
            UUID executionId = UUID.fromString(JsonPath.read(
                    result.getResponse().getContentAsString(), "$.execution_id"
            ));
            TaskExecution execution = executionStore.findById(executionId).orElseThrow();
            await().atMost(2, SECONDS).until(execution::isTerminal);
            final TaskExecutionStatus actual = execution.getStatus();

            // Then
            final TaskExecutionStatus expected = TaskExecutionStatus.SKIPPED;
            assertThat(actual).isEqualTo(expected);
            assertThat(execution.getStatusReason()).isEqualTo("CONCURRENT_EXECUTION");
        } finally {
            blockingConcurrencyTask.release();
            firstExecution.get(2, SECONDS);
        }
    }

    private String taskKey() {
        return cronctl.getAllTasks().stream()
                .filter(task -> "Concurrency Policy Test Task".equals(task.getLabel()))
                .findFirst()
                .orElseThrow()
                .getTaskKey();
    }

    @TestConfiguration
    static class ConcurrencyPolicyTestConfiguration {

        @Bean
        public BlockingConcurrencyTask blockingConcurrencyTask() {
            return new BlockingConcurrencyTask();
        }

    }

    static class BlockingConcurrencyTask {

        private volatile CountDownLatch started = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(1);

        @CronctlTask(
                label = "Concurrency Policy Test Task",
                concurrency = ConcurrencyPolicy.SKIP,
                maxConcurrentExecutions = 1
        )
        @Scheduled(fixedRate = 1, initialDelay = 1, timeUnit = HOURS)
        public void run() throws InterruptedException {
            started.countDown();
            release.await();
        }

        private boolean awaitStarted() throws InterruptedException {
            return started.await(2, SECONDS);
        }

        private void reset() {
            started = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        private void release() {
            release.countDown();
        }

    }

}
