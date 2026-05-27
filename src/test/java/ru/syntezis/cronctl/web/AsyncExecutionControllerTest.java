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
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.core.async.ExecutionRegistry;
import ru.syntezis.cronctl.domain.execution.TaskExecution;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;

import java.util.UUID;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
    private ExecutionRegistry executionRegistry;

    @Test
    void submitExecution_KnownTask_Returns202WithExecutionId() throws Exception {
        // Given
        UUID taskId = fastTaskId();

        // When / Then
        mockMvc.perform(post("/api/cronctl/tasks/{taskId}/executions", taskId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.execution_id").isNotEmpty())
                .andExpect(jsonPath("$.task_id").value(taskId.toString()))
                .andExpect(jsonPath("$.status").isNotEmpty())
                .andExpect(jsonPath("$.submitted_at").isNotEmpty());
    }

    @Test
    void submitExecution_UnknownTaskId_Returns404() throws Exception {
        mockMvc.perform(post("/api/cronctl/tasks/{taskId}/executions", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getExecution_ExistingExecution_Returns200WithStatus() throws Exception {
        // Given
        UUID executionId = submitAndExtractExecutionId(fastTaskId());

        // When / Then
        mockMvc.perform(get("/api/cronctl/executions/{executionId}", executionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution_id").value(executionId.toString()))
                .andExpect(jsonPath("$.status").isNotEmpty());
    }

    @Test
    void getExecution_UnknownExecutionId_Returns404() throws Exception {
        mockMvc.perform(get("/api/cronctl/executions/{executionId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelExecution_UnknownExecutionId_Returns404() throws Exception {
        mockMvc.perform(delete("/api/cronctl/executions/{executionId}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelExecution_TerminalExecution_Returns409() throws Exception {
        // Given — submit fast task and wait for it to complete
        UUID executionId = submitAndExtractExecutionId(fastTaskId());
        TaskExecution execution = executionRegistry.getById(executionId).orElseThrow();
        await().atMost(3, SECONDS).until(execution::isTerminal);

        // When / Then
        mockMvc.perform(delete("/api/cronctl/executions/{executionId}", executionId))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelExecution_ActiveExecution_Returns204() throws Exception {
        // Given — submit blocking task
        UUID blockingTaskId = findTaskIdByLabel("Async Controller Test Blocking Task");
        UUID executionId = submitAndExtractExecutionId(blockingTaskId);

        // When / Then
        mockMvc.perform(delete("/api/cronctl/executions/{executionId}", executionId))
                .andExpect(status().isNoContent());
    }

    @Test
    void listExecutions_NoFilter_Returns200WithExecutionsArray() throws Exception {
        // Given — submit any task so the list is non-empty
        submitAndExtractExecutionId(fastTaskId());

        // When / Then
        mockMvc.perform(get("/api/cronctl/executions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions").isArray())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    void listExecutions_FilterByRunningStatus_Returns200WithMatchingExecutions() throws Exception {
        // Given — submit a blocking task (stays in RUNNING)
        UUID blockingTaskId = findTaskIdByLabel("Async Controller Test Blocking Task");
        UUID executionId = submitAndExtractExecutionId(blockingTaskId);
        TaskExecution execution = executionRegistry.getById(executionId).orElseThrow();
        await().atMost(3, SECONDS).until(() -> execution.getState() == TaskExecutionStatus.RUNNING);

        // When / Then
        mockMvc.perform(get("/api/cronctl/executions").param("status", "RUNNING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions").isArray())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void listExecutions_FilterBySucceededStatus_Returns200WithOnlySucceededExecutions() throws Exception {
        // Given — submit a fast task and wait for it to succeed
        UUID executionId = submitAndExtractExecutionId(fastTaskId());
        TaskExecution execution = executionRegistry.getById(executionId).orElseThrow();
        await().atMost(3, SECONDS).until(execution::isTerminal);

        // When / Then
        mockMvc.perform(get("/api/cronctl/executions").param("status", "SUCCEEDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executions").isArray())
                .andExpect(jsonPath("$.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    private UUID fastTaskId() {
        return findTaskIdByLabel("scheduledTask");
    }

    private UUID findTaskIdByLabel(String label) {
        return cronctl.getAllTasks().stream()
                .filter(task -> label.equals(task.getLabel()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private UUID submitAndExtractExecutionId(UUID taskId) throws Exception {
        String response = mockMvc.perform(post("/api/cronctl/tasks/{taskId}/executions", taskId))
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
