package ru.syntezis.cronctl.web;

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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CronctlNextExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Cronctl cronctl;

    @Test
    void getNextExecutionTime_UnknownId_Returns404() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks/{id}/next-execution", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getNextExecutionTime_CronTask_Returns200AndNextExecutionAtIsPresent() throws Exception {
        final UUID id = cronTaskId();

        mockMvc.perform(get("/api/cronctl/tasks/{id}/next-execution", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task_id").value(id.toString()))
                .andExpect(jsonPath("$.next_execution_at").isNotEmpty());
    }

    @Test
    void getNextExecutionTime_FixedRateTask_Returns200AndNextExecutionAtIsPresent() throws Exception {
        final UUID id = fixedRateTaskId();

        mockMvc.perform(get("/api/cronctl/tasks/{id}/next-execution", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task_id").value(id.toString()))
                .andExpect(jsonPath("$.next_execution_at").isNotEmpty());
    }

    @Test
    void getScheduledTasks_CronTask_NextExecutionAtPresentInResponse() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[?(@.label == 'next-exec-cron')].next_execution_at").isNotEmpty());
    }

    @Test
    void getScheduledTasks_FixedRateTask_NextExecutionAtPresentInResponse() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[?(@.label == 'next-exec-fixed')].next_execution_at").isNotEmpty());
    }

    @Test
    void getScheduledTasks_NoTimeoutTask_TimeoutReturnedWithoutMappingFailure() throws Exception {
        // Given
        final long expected = CronctlTask.NO_TIMEOUT;

        // When
        final ResultActions actual = mockMvc.perform(get("/api/cronctl/tasks")
                .param("group", "no-timeout"));

        // Then
        actual.andExpect(status().isOk())
                .andExpect(jsonPath("$.tasks[0].timeout_seconds").value(expected));
    }

    private UUID cronTaskId() {
        return cronctl.getAllTasks().stream()
                .filter(task -> "next-exec-cron".equals(task.getLabel()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private UUID fixedRateTaskId() {
        return cronctl.getAllTasks().stream()
                .filter(task -> "next-exec-fixed".equals(task.getLabel()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    @TestConfiguration
    static class NextExecutionTestConfig {

        @Bean
        public NextExecutionScheduler nextExecutionScheduler() {
            return new NextExecutionScheduler();
        }

    }

    static class NextExecutionScheduler {

        @CronctlTask(label = "next-exec-cron")
        @Scheduled(cron = "0 * * * * *")
        public void cronTask() {}

        @CronctlTask(
                label = "next-exec-fixed",
                group = "no-timeout",
                timeout = CronctlTask.NO_TIMEOUT
        )
        @Scheduled(fixedRate = Integer.MAX_VALUE, initialDelay = Integer.MAX_VALUE)
        public void fixedRateTask() {}

    }
}
