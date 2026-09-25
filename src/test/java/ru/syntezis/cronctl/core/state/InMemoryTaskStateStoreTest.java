package ru.syntezis.cronctl.core.state;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTaskStateStoreTest {

    private final InMemoryTaskStateStore underTest = new InMemoryTaskStateStore();

    @Test
    void markPaused_UnknownTask_TaskMarkedPaused() {
        // Given
        String taskKey = "billing.reconciliation";

        // When
        underTest.markPaused(taskKey);
        final boolean actual = underTest.isPaused(taskKey);

        // Then
        assertThat(actual).isTrue();
    }

    @Test
    void clearPaused_PausedTask_TaskMarkedEnabled() {
        // Given
        String taskKey = "billing.reconciliation";
        underTest.markPaused(taskKey);

        // When
        underTest.clearPaused(taskKey);
        final boolean actual = underTest.isPaused(taskKey);

        // Then
        assertThat(actual).isFalse();
    }

    @Test
    void findPausedTaskKeys_PausedTasks_ImmutableSnapshotReturned() {
        // Given
        final Set<String> expected = Set.of("billing.reconciliation");
        underTest.markPaused("billing.reconciliation");

        // When
        final Set<String> actual = underTest.findPausedTaskKeys();
        underTest.markPaused("reports.daily");

        // Then
        assertThat(actual).isEqualTo(expected);
    }

}
