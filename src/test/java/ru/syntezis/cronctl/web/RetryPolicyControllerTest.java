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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.enums.RetryBackoff;

import java.util.List;
import java.util.Map;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "cronctl.api.public-access=true")
@AutoConfigureMockMvc
class RetryPolicyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Cronctl cronctl;

    @Test
    void getScheduledTasks_RetryPolicyConfigured_PolicyFieldsReturned() throws Exception {
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
        assertThat(actual.get("retries")).isEqualTo(0);
        assertThat(actual.get("retry_delay")).isEqualTo("PT10S");
        assertThat(actual.get("retry_backoff")).isEqualTo("EXPONENTIAL");
        assertThat(actual.get("max_retry_delay")).isEqualTo("PT5M");
    }

    @Test
    void retryExecution_LeafFailedExecution_RetryAcceptedAndDuplicateRejected() throws Exception {
        // Given
        String taskKey = taskKey();
        MvcResult failedResult = mockMvc.perform(post("/api/cronctl/tasks/{taskKey}/execute", taskKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andReturn();
        String executionId = JsonPath.read(failedResult.getResponse().getContentAsString(), "$.execution_id");

        // When
        final ResultActions actual = mockMvc.perform(
                post("/api/cronctl/executions/{executionId}/retry", executionId)
        );

        // Then
        actual.andExpect(status().isAccepted())
                .andExpect(jsonPath("$.source").value("RETRY"))
                .andExpect(jsonPath("$.retry_trigger").value("MANUAL"))
                .andExpect(jsonPath("$.parent_execution_id").value(executionId))
                .andExpect(jsonPath("$.attempt").value(1));
        mockMvc.perform(post("/api/cronctl/executions/{executionId}/retry", executionId))
                .andExpect(status().isConflict());
    }

    @Test
    void retryAllFailed_LatestLeafFailurePerTask_RetrySubmitted() throws Exception {
        // Given
        String taskKey = taskKey();
        mockMvc.perform(post("/api/cronctl/tasks/{taskKey}/execute", taskKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // When
        final ResultActions actual = mockMvc.perform(post("/api/cronctl/executions/retry-all-failed"));

        // Then
        actual.andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.executions[?(@.task_key == '" + taskKey + "')]").isNotEmpty());
    }

    private String taskKey() {
        return cronctl.getAllTasks().stream()
                .filter(task -> "Retry API Test Task".equals(task.getLabel()))
                .findFirst()
                .orElseThrow()
                .getTaskKey();
    }

    @TestConfiguration
    static class RetryPolicyTestConfiguration {

        @Bean
        public AlwaysFailingRetryTask alwaysFailingRetryTask() {
            return new AlwaysFailingRetryTask();
        }

    }

    static class AlwaysFailingRetryTask {

        @CronctlTask(
                label = "Retry API Test Task",
                retries = 0,
                retryDelay = "PT10S",
                retryBackoff = RetryBackoff.EXPONENTIAL
        )
        @Scheduled(fixedRate = 1, initialDelay = 1, timeUnit = HOURS)
        public void run() {
            throw new IllegalStateException("retry api test failure");
        }

    }

}
