package ru.syntezis.cronctl.web;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.core.execution.ExecutionStore;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "cronctl.executor.timeout-seconds=0")
@AutoConfigureMockMvc
class AsyncExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Cronctl cronctl;

    @Autowired
    private ExecutionStore executionStore;

    @Test
    void submitExecution_KnownTask_Returns202WithExecutionId() throws Exception {
        // Given
        final String taskKey = fastTaskKey();

        // When
        final ResultActions actual = mockMvc.perform(post("/api/cronctl/tasks/{taskKey}/executions", taskKey));

        // Then
        actual.andExpect(status().isAccepted())
                .andExpect(jsonPath("$.execution_id").isNotEmpty())
                .andExpect(jsonPath("$.task_key").value(taskKey))
                .andExpect(jsonPath("$.source").value("MANUAL_ASYNC"))
                .andExpect(jsonPath("$.status").isNotEmpty())
                .andExpect(jsonPath("$.created_at").isNotEmpty());
    }

    @Test
    void submitExecution_UnknownTaskKey_Returns404() throws Exception {
        // Given
        final String taskKey = "missing.task";

        // When
        final ResultActions actual = mockMvc.perform(post("/api/cronctl/tasks/{taskKey}/executions", taskKey));

        // Then
        actual.andExpect(status().isNotFound());
    }

    @Test
    void getExecution_ExistingExecution_Returns200WithStatus() throws Exception {
        // Given
        UUID executionId = submitAndExtractExecutionId(fastTaskKey());

        // When
        final ResultActions actual = mockMvc.perform(get("/api/cronctl/executions/{executionId}", executionId));

        // Then
        actual.andExpect(status().isOk())
                .andExpect(jsonPath("$.execution_id").value(executionId.toString()))
                .andExpect(jsonPath("$.status").isNotEmpty());
    }

    @Test
    void getExecution_UnknownExecutionId_Returns404() throws Exception {
        // Given
        final UUID executionId = UUID.randomUUID();

        // When
        final ResultActions actual = mockMvc.perform(get("/api/cronctl/executions/{executionId}", executionId));

        // Then
        actual.andExpect(status().isNotFound());
    }

    @Test
    void cancelExecution_UnknownExecutionId_Returns404() throws Exception {
        // Given
        final UUID executionId = UUID.randomUUID();

        // When
        final ResultActions actual = mockMvc.perform(delete("/api/cronctl/executions/{executionId}", executionId));

        // Then
        actual.andExpect(status().isNotFound());
    }

    @Test
    void cancelExecution_TerminalExecution_Returns409() throws Exception {
        // Given
        UUID executionId = submitAndExtractExecutionId(fastTaskKey());
        TaskExecution execution = executionStore.findById(executionId).orElseThrow();
        await().atMost(3, SECONDS).until(execution::isTerminal);

        // When
        final ResultActions actual = mockMvc.perform(delete("/api/cronctl/executions/{executionId}", executionId));

        // Then
        actual.andExpect(status().isConflict());
    }

    @Test
    void cancelExecution_ActiveExecution_Returns204() throws Exception {
        // Given
        String blockingTaskKey = findTaskKeyByLabel("Async Controller Test Blocking Task");
        UUID executionId = submitAndExtractExecutionId(blockingTaskKey);

        // When
        final ResultActions actual = mockMvc.perform(delete("/api/cronctl/executions/{executionId}", executionId));

        // Then
        actual.andExpect(status().isNoContent());
    }

    @Test
    void listExecutions_NoFilter_Returns200WithExecutionsArray() throws Exception {
        // Given
        submitAndExtractExecutionId(fastTaskKey());

        // When
        final ResultActions actual = mockMvc.perform(get("/api/cronctl/executions"));

        // Then
        actual.andExpect(status().isOk())
                .andExpect(jsonPath("$.executions").isArray())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.has_next").isBoolean());
    }

    @Test
    void listExecutions_FilterByRunningStatus_Returns200WithMatchingExecutions() throws Exception {
        // Given
        String blockingTaskKey = findTaskKeyByLabel("Async Controller Test Blocking Task");
        UUID executionId = submitAndExtractExecutionId(blockingTaskKey);
        TaskExecution execution = executionStore.findById(executionId).orElseThrow();
        await().atMost(3, SECONDS).until(() -> execution.getStatus() == TaskExecutionStatus.RUNNING);

        // When
        final ResultActions actual = mockMvc.perform(get("/api/cronctl/executions")
                .param("status", "RUNNING")
                .param("source", "MANUAL_ASYNC")
                .param("taskKey", blockingTaskKey)
                .param("page", "0")
                .param("size", "1"));

        // Then
        actual.andExpect(status().isOk())
                .andExpect(jsonPath("$.executions").isArray())
                .andExpect(jsonPath("$.executions[0].source").value("MANUAL_ASYNC"))
                .andExpect(jsonPath("$.executions[0].task_key").value(blockingTaskKey))
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void listExecutions_FilterBySucceededStatus_Returns200WithOnlySucceededExecutions() throws Exception {
        // Given
        UUID executionId = submitAndExtractExecutionId(fastTaskKey());
        TaskExecution execution = executionStore.findById(executionId).orElseThrow();
        await().atMost(3, SECONDS).until(execution::isTerminal);

        // When
        final ResultActions actual = mockMvc.perform(get("/api/cronctl/executions").param("status", "SUCCEEDED"));

        // Then
        actual.andExpect(status().isOk())
                .andExpect(jsonPath("$.executions").isArray())
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(1)));
    }

    private String fastTaskKey() {
        return findTaskKeyByLabel("scheduledTask");
    }

    private String findTaskKeyByLabel(String label) {
        return cronctl.getAllTasks().stream()
                .filter(task -> label.equals(task.getLabel()))
                .findFirst()
                .orElseThrow()
                .getTaskKey();
    }

    private UUID submitAndExtractExecutionId(String taskKey) throws Exception {
        String response = mockMvc.perform(post("/api/cronctl/tasks/{taskKey}/executions", taskKey))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.execution_id"));
    }

    @TestConfiguration
    static class AsyncControllerTestConfig {

        @Bean
        public BlockingScheduler blockingScheduler() {
            return new BlockingScheduler();
        }

    }

    static class BlockingScheduler {

        @CronctlTask(label = "Async Controller Test Blocking Task")
        @Scheduled(fixedRate = Long.MAX_VALUE)
        public void blockingTask() throws InterruptedException {
            Thread.sleep(Long.MAX_VALUE); // NOSONAR
        }
    }
}
