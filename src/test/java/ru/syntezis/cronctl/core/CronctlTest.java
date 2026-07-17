package ru.syntezis.cronctl.core;

import io.vavr.control.Try;
import org.apache.commons.lang3.tuple.Pair;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.util.Repeats;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class CronctlTest {

    @Spy
    private final TaskRegistry registry = new TaskRegistry();

    @InjectMocks
    private Cronctl underTest;

    private static final EasyRandom random = new EasyRandom(new EasyRandomParameters()
            .randomize(Method.class, () -> Try.of(() -> Object.class.getDeclaredMethod("toString")).get())
            .excludeField(f -> f.getName().equals("scheduledMethodId"))
    );

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 10, 100, 999})
    void getAllTasks_ThreeTasksInRegistry_ListReturned(int tasksCount) {
        // Given
        List<Pair<UUID, Task>> tasks = Repeats.supplierRepeat(tasksCount, () -> {
            UUID id = UUID.randomUUID();
            Task method = random.nextObject(Task.class);
            return Pair.of(id, method);
        });

        registry.addAll(tasks);

        final List<Task> expected = registry.getAll();

        // When
        final List<Task> actual = underTest.getAllTasks();

        // Then
        assertThat(actual)
                .containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void getByTag_TasksWithMatchingTag_ReturnsOnlyMatchingTasks() {
        // Given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Task withTag    = buildTask(id1, "withTag",    List.of("billing"));
        Task withoutTag = buildTask(id2, "withoutTag", List.of("other"));
        registry.add(id1, withTag);
        registry.add(id2, withoutTag);

        // When
        final List<Task> actual = underTest.getByTag("billing");

        // Then
        assertThat(actual).containsExactly(withTag);
    }

    @Test
    void getByTag_NoMatchingTag_ReturnsEmptyList() {
        // Given
        UUID id = UUID.randomUUID();
        registry.add(id, buildTask(id, "task", List.of("alpha")));

        // When
        final List<Task> actual = underTest.getByTag("nonexistent");

        // Then
        assertThat(actual).isEmpty();
    }

    @Test
    void getByGroup_TasksInMatchingGroup_ReturnsOnlyMatchingTasks() {
        // Given
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Task inGroup    = buildTaskWithGroup(id1, "inGroup",    "reports");
        Task otherGroup = buildTaskWithGroup(id2, "otherGroup", "billing");
        registry.add(id1, inGroup);
        registry.add(id2, otherGroup);

        // When
        final List<Task> actual = underTest.getByGroup("reports");

        // Then
        assertThat(actual).containsExactly(inGroup);
    }

    @Test
    void getByGroup_NoMatchingGroup_ReturnsEmptyList() {
        // Given
        UUID id = UUID.randomUUID();
        registry.add(id, buildTaskWithGroup(id, "task", "someGroup"));

        // When
        final List<Task> actual = underTest.getByGroup("nonexistent");

        // Then
        assertThat(actual).isEmpty();
    }

    @Test
    void getAllTasks_RegistryIsEmpty_EmptyListReturned() {
        // Given
        registry.clear();

        // When
        final List<Task> actual = underTest.getAllTasks();

        // Then
        assertThat(actual)
                .isEmpty();
    }

    @Test
    void taskExists_taskExistsInRegistry_ReturnTrue() {
        // Given
        UUID scheduledMethodId = UUID.randomUUID();
        registry.add(scheduledMethodId, random.nextObject(Task.class));

        // When
        final boolean actual = underTest.taskExists(scheduledMethodId);

        // Then
        assertThat(actual)
                .isTrue();
    }

    @Test
    void taskExists_taskNotExistsInRegistry_ReturnFalse() {
        // Given
        registry.clear();

        // When
        final boolean actual = underTest.taskExists(UUID.randomUUID());

        // Then
        assertThat(actual)
                .isFalse();
    }

    @Test
    void getById_taskExistsInRegistry_ReturnFilledOptional() {
        // Given
        UUID scheduledMethodId = UUID.randomUUID();
        final Task expected = random.nextObject(Task.class);
        expected.getDetails().setId(scheduledMethodId);

        registry.add(scheduledMethodId, expected);

        // When
        final Optional<Task> actual = underTest.getById(scheduledMethodId);

        // Then
        assertThat(actual)
                .isPresent()
                .get()
                .isEqualTo(expected);
    }

    @Test
    void getById_taskNotExistsInRegistry_ReturnEmptyOptional() {
        // Given
        registry.clear();

        // When
        final Optional<Task> actual = underTest.getById(UUID.randomUUID());

        // Then
        assertThat(actual)
                .isEmpty();
    }

    @AfterEach
    void cleanup() {
        registry.clear();
    }

    private static Task buildTask(UUID id, String methodName, List<String> tags) {
        return Task.builder()
                .label(methodName)
                .description(methodName)
                .group("default")
                .tags(tags)
                .details(ScheduledMethodDetails.builder()
                        .id(id)
                        .methodName(methodName)
                        .build())
                .build();
    }

    private static Task buildTaskWithGroup(UUID id, String methodName, String group) {
        return Task.builder()
                .label(methodName)
                .description(methodName)
                .group(group)
                .tags(List.of())
                .details(ScheduledMethodDetails.builder()
                        .id(id)
                        .methodName(methodName)
                        .build())
                .build();
    }

    private static class SampleScheduledThrowingClass {

        @Scheduled(fixedRate = 1000L)
        private void throwingJob() {
            throw new RuntimeException("Error");
        }
    }
}