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
import ru.syntezis.cronctl.domain.task.Task;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CronctlToggleControllerTest {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(3);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Cronctl cronctl;

    @Autowired
    private ToggleScheduler toggleScheduler;

    @Test
    void disableAndEnable_TogglingEnabledTask_ScheduledExecutionPausedAndResumed() throws Exception {
        // Given
        Task task = togglingEnabledTask();
        await(() -> toggleScheduler.executionCount() > 0);

        // When
        mockMvc.perform(post("/api/cronctl/tasks/{id}/disable", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.toggling_enabled").value(true));
        mockMvc.perform(post("/api/cronctl/tasks/{id}/execute", task.getId()))
                .andExpect(status().isOk());

        // Then
        Thread.sleep(150L);
        final int expected = toggleScheduler.executionCount();
        Thread.sleep(200L);
        final int actual = toggleScheduler.executionCount();
        assertThat(actual)
                .isEqualTo(expected);

        // When
        mockMvc.perform(post("/api/cronctl/tasks/{id}/disable", task.getId())
                        .param("interrupt", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
        mockMvc.perform(post("/api/cronctl/tasks/{id}/enable", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.toggling_enabled").value(true));

        // Then
        await(() -> toggleScheduler.executionCount() > actual);
        mockMvc.perform(post("/api/cronctl/tasks/{id}/enable", task.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void disable_UnknownTask_Returns404() throws Exception {
        // Given
        UUID taskId = UUID.randomUUID();

        // When
        final ResultActions actual = mockMvc.perform(post("/api/cronctl/tasks/{id}/disable", taskId));

        // Then
        actual.andExpect(status().isNotFound());
    }

    @Test
    void enable_UnknownTask_Returns404() throws Exception {
        // Given
        UUID taskId = UUID.randomUUID();

        // When
        final ResultActions actual = mockMvc.perform(post("/api/cronctl/tasks/{id}/enable", taskId));

        // Then
        actual.andExpect(status().isNotFound());
    }

    @Test
    void disable_TogglingDisabledTask_Returns409() throws Exception {
        // Given
        Task task = togglingDisabledTask();

        // When
        final ResultActions actual = mockMvc.perform(post("/api/cronctl/tasks/{id}/disable", task.getId()));

        // Then
        actual.andExpect(status().isConflict());
    }

    @Test
    void enable_TogglingDisabledTask_Returns409() throws Exception {
        // Given
        Task task = togglingDisabledTask();

        // When
        final ResultActions actual = mockMvc.perform(post("/api/cronctl/tasks/{id}/enable", task.getId()));

        // Then
        actual.andExpect(status().isConflict());
    }

    private Task togglingEnabledTask() {
        return cronctl.getAllTasks().stream()
                .filter(task -> "toggle-test-task".equals(task.getLabel()))
                .findFirst()
                .orElseThrow();
    }

    private Task togglingDisabledTask() {
        return cronctl.getAllTasks().stream()
                .filter(task -> !task.isTogglingEnabled())
                .findFirst()
                .orElseThrow();
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(WAIT_TIMEOUT);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            Thread.sleep(20L);
        }
        assertThat(condition.getAsBoolean())
                .isTrue();
    }

    @TestConfiguration
    static class ToggleTestConfiguration {

        @Bean
        public ToggleScheduler toggleScheduler() {
            return new ToggleScheduler();
        }

    }

    static class ToggleScheduler {

        private final AtomicInteger executions = new AtomicInteger();

        @CronctlTask(label = "toggle-test-task", togglingEnabled = true)
        @Scheduled(fixedRate = 50L, initialDelay = 100L)
        public void run() {
            executions.incrementAndGet();
        }

        public int executionCount() {
            return executions.get();
        }

    }
}
