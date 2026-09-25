package ru.syntezis.cronctl.core.state;

import java.util.Set;

/**
 * Pluggable, thread-safe storage contract for task pause markers.
 * The absence of a marker means that automatic execution is enabled.
 */
public interface TaskStateStore {

    /** Returns whether automatic execution is persistently paused for the task. */
    boolean isPaused(String taskKey);

    /** Returns a snapshot of all persisted pause markers. */
    Set<String> findPausedTaskKeys();

    /** Creates an idempotent pause marker. */
    void markPaused(String taskKey);

    /** Removes a pause marker if it exists. */
    void clearPaused(String taskKey);

}
