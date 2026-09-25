package ru.syntezis.cronctl.core.state;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import ru.syntezis.cronctl.core.StateToggler;
import ru.syntezis.cronctl.core.TaskRegistry;
import ru.syntezis.cronctl.domain.task.Task;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Restores persisted pause markers after Spring has registered the application schedules. */
@Slf4j
@RequiredArgsConstructor
public class TaskStateRestorer implements ApplicationListener<ContextRefreshedEvent>, Ordered {

    private final TaskRegistry taskRegistry;
    private final StateToggler stateToggler;
    private final TaskStateStore taskStateStore;
    private final AtomicBoolean restored = new AtomicBoolean();

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (!restored.compareAndSet(false, true)) {
            return;
        }

        taskStateStore.findPausedTaskKeys().forEach(this::restorePausedTask);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private void restorePausedTask(String taskKey) {
        Optional<Task> registeredTask = taskRegistry.getById(taskKey);
        if (registeredTask.isEmpty()) {
            log.debug("Paused task {} is not registered; keeping its state marker", taskKey);
            return;
        }

        Task task = registeredTask.orElseThrow();
        if (!task.isTogglingEnabled()) {
            log.warn("Task {} no longer supports toggling; removing its stale pause marker", taskKey);
            taskStateStore.clearPaused(taskKey);
            return;
        }

        try {
            stateToggler.disable(task, false);
            log.info("Restored paused state for task {}", taskKey);
        } catch (RuntimeException e) {
            log.error("Failed to restore paused state for task {}; automatic invocations remain guarded", taskKey, e);
        }
    }
}
