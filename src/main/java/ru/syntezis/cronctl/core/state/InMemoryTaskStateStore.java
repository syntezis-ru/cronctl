package ru.syntezis.cronctl.core.state;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe, process-local task pause store. */
public class InMemoryTaskStateStore implements TaskStateStore {

    private final Set<String> pausedTaskKeys = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isPaused(String taskKey) {
        return pausedTaskKeys.contains(taskKey);
    }

    @Override
    public Set<String> findPausedTaskKeys() {
        return Set.copyOf(pausedTaskKeys);
    }

    @Override
    public void markPaused(String taskKey) {
        pausedTaskKeys.add(taskKey);
    }

    @Override
    public void clearPaused(String taskKey) {
        pausedTaskKeys.remove(taskKey);
    }
}
