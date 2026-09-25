package ru.syntezis.cronctl.core.state;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.event.ContextRefreshedEvent;
import ru.syntezis.cronctl.core.StateToggler;
import ru.syntezis.cronctl.core.TaskRegistry;
import ru.syntezis.cronctl.domain.task.Task;

import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskStateRestorerTest {

    @Mock
    private TaskRegistry taskRegistry;

    @Mock
    private StateToggler stateToggler;

    @Mock
    private TaskStateStore taskStateStore;

    @Mock
    private ContextRefreshedEvent event;

    @Mock
    private Task task;

    @InjectMocks
    private TaskStateRestorer underTest;

    @Test
    void onApplicationEvent_PersistedPausedTask_TaskDisabled() {
        // Given
        String taskKey = "billing.reconciliation";
        when(taskStateStore.findPausedTaskKeys()).thenReturn(Set.of(taskKey));
        when(taskRegistry.getById(taskKey)).thenReturn(Optional.of(task));
        when(task.isTogglingEnabled()).thenReturn(true);

        // When
        underTest.onApplicationEvent(event);

        // Then
        verify(stateToggler).disable(task, false);
    }

    @Test
    void onApplicationEvent_UnknownPersistedTask_StateMarkerKept() {
        // Given
        String taskKey = "removed.task";
        when(taskStateStore.findPausedTaskKeys()).thenReturn(Set.of(taskKey));
        when(taskRegistry.getById(taskKey)).thenReturn(Optional.empty());

        // When
        underTest.onApplicationEvent(event);

        // Then
        verify(taskStateStore, never()).clearPaused(taskKey);
        verifyNoInteractions(stateToggler);
    }

    @Test
    void onApplicationEvent_TaskNoLongerSupportsToggling_StateMarkerRemoved() {
        // Given
        String taskKey = "billing.reconciliation";
        when(taskStateStore.findPausedTaskKeys()).thenReturn(Set.of(taskKey));
        when(taskRegistry.getById(taskKey)).thenReturn(Optional.of(task));
        when(task.isTogglingEnabled()).thenReturn(false);

        // When
        underTest.onApplicationEvent(event);

        // Then
        verify(taskStateStore).clearPaused(taskKey);
        verifyNoInteractions(stateToggler);
    }

    @Test
    void onApplicationEvent_RepeatedContextRefresh_StateRestoredOnce() {
        // Given
        when(taskStateStore.findPausedTaskKeys()).thenReturn(Set.of());
        underTest.onApplicationEvent(event);

        // When
        underTest.onApplicationEvent(event);

        // Then
        verify(taskStateStore).findPausedTaskKeys();
    }
}
