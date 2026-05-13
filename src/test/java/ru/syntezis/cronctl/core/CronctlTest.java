package ru.syntezis.cronctl.core;

import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import ru.syntezis.cronctl.domain.*;
import ru.syntezis.cronctl.enums.TaskExecutionStatus;
import ru.syntezis.cronctl.scan.TaskRegistry;
import ru.syntezis.cronctl.util.Repeats;
import ru.syntezis.cronctl.util.ScheduleUtils;
import ru.syntezis.cronctl.util.condition.Conditions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CronctlTest {

    @Spy
    private final TaskRegistry registry = new TaskRegistry();

    @Spy
    private final TaskExecutor executor = new TaskExecutor();

    @InjectMocks
    private Cronctl underTest;

    private static final EasyRandom random = new EasyRandom(new EasyRandomParameters()
            .randomize(Method.class, () ->
                    Try.of(() -> Object.class.getDeclaredMethod("toString"))
                            .get()
            )
            .excludeField(f -> f.getName().equals("scheduledMethodId"))
    );

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 10, 100, 999})
    void getAllTasks_ThreeTasksInRegistry_ListReturned(int tasksCount) {
        // Given
        List<Pair<UUID, ScheduledMethod>> methods = Repeats.supplierRepeat(tasksCount, () -> {
            UUID id = UUID.randomUUID();
            ScheduledMethod method = random.nextObject(ScheduledMethod.class);
            return Pair.of(id, method);
        });

        registry.addAll(methods);

        final List<Task> expected = methods.stream()
                .map(p -> new Task(p.getRight()))
                .toList();

        // When
        final List<Task> actual = underTest.getAllTasks();

        // Then
        assertThat(actual)
                .containsExactlyInAnyOrderElementsOf(expected);
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
        registry.add(scheduledMethodId, random.nextObject(ScheduledMethod.class));

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
        final Task expected = new Task(random.nextObject(ScheduledMethod.class));
        expected.getMethod().getDetails().setId(scheduledMethodId);

        registry.add(scheduledMethodId, expected.getMethod());

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

    @Test
    void executeTaskByID_taskExecutedSuccessfully_TaskExecutedAndReturnedExecutionDetails() {
        // Given
        UUID scheduledMethodId = UUID.randomUUID();
        Task task = new Task(random.nextObject(ScheduledMethod.class));
        task.getMethod().getDetails().setId(scheduledMethodId);

        registry.add(scheduledMethodId, task.getMethod());

        // When
        final TaskExecutionDetails actual = underTest.executeTaskByID(scheduledMethodId);

        // Then
        verify(executor).executeTask(task);

        assertThat(actual)
                .hasNoNullFieldsOrPropertiesExcept("failDetails")
                .hasFieldOrPropertyWithValue("scheduledMethodId", scheduledMethodId)
                .hasFieldOrPropertyWithValue("status", TaskExecutionStatus.SUCCEEDED)
                .satisfies(new Conditions.TaskExecutionDetailsUUIDCondition());
    }

    @Test
    void executeTaskByID_taskDidNotExecutedSuccessfully_TaskExecutedAndReturnedExecutionDetails() throws NoSuchMethodException {
        // Given
        UUID scheduledMethodId = UUID.randomUUID();
        SampleScheduledThrowingClass bean = new SampleScheduledThrowingClass();
        Method method = SampleScheduledThrowingClass.class.getDeclaredMethod("throwingJob");
        Task task = Task.builder()
                .method(ScheduledMethod.builder()
                        .details(ScheduledMethodDetails.builder()
                                .id(scheduledMethodId)
                                .methodName("throwingJob")
                                .schedule(ScheduleUtils.assembleScheduleDetails(method.getAnnotation(Scheduled.class)))
                                .build()
                        )
                        .reference(ScheduledMethodReference.builder()
                                .beanName("")
                                .bean(bean)
                                .method(method)
                                .build()
                        )
                        .build()
                )
                .build();

        registry.add(scheduledMethodId, task.getMethod());

        // When
        final TaskExecutionDetails actual = underTest.executeTaskByID(scheduledMethodId);

        // Then
        verify(executor).executeTask(task);

        assertThat(actual)
                .hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("scheduledMethodId", scheduledMethodId)
                .hasFieldOrPropertyWithValue("status", TaskExecutionStatus.FAILED)
                .satisfies(new Conditions.TaskExecutionDetailsUUIDCondition())
                .extracting(TaskExecutionDetails::getFailDetails)
                .hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("message", "Error")
                .extracting(TaskExecutionDetails.FailDetails::getThrowable)
                .asInstanceOf(InstanceOfAssertFactories.THROWABLE)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Error");
    }

    @AfterEach
    void cleanup() {
        registry.clear();
    }

    private static class SampleScheduledThrowingClass {

        @Scheduled(fixedRate = 1000L)
        private void throwingJob() {
            throw new RuntimeException("Error");
        }
    }
}