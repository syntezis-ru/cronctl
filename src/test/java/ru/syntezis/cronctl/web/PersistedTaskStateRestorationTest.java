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
import ru.syntezis.cronctl.core.state.InMemoryTaskStateStore;
import ru.syntezis.cronctl.core.state.TaskStateStore;
import ru.syntezis.cronctl.domain.task.Task;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PersistedTaskStateRestorationTest {

    private static final String TASK_KEY = "startup.paused";

    @Autowired
    private Cronctl cronctl;

    @Autowired
    private PausedScheduler pausedScheduler;

    @Test
    void applicationStartup_PersistedPause_TaskDisabledBeforeInvocation() {
        // Given
        final boolean expected = false;

        // When
        final Task actual = cronctl.getById(TASK_KEY).orElseThrow();

        // Then
        assertThat(actual.isEnabled()).isEqualTo(expected);
        await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> assertThat(pausedScheduler.getInvocationCount()).hasValue(0));
    }

    @TestConfiguration
    static class PausedStateConfiguration {

        @Bean
        TaskStateStore persistentTaskStateStore() {
            InMemoryTaskStateStore taskStateStore = new InMemoryTaskStateStore();
            taskStateStore.markPaused(TASK_KEY);
            return taskStateStore;
        }

        @Bean
        PausedScheduler pausedScheduler() {
            return new PausedScheduler();
        }

    }

    static class PausedScheduler {

        @Getter
        private final AtomicInteger invocationCount = new AtomicInteger();

        @CronctlTask(id = TASK_KEY, togglingEnabled = true)
        @Scheduled(initialDelay = 0L, fixedDelay = 3_600_000L)
        public void scheduledMethod() {
            invocationCount.incrementAndGet();
        }

    }

}
