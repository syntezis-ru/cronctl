package ru.syntezis.cronctl.bpp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;
import ru.syntezis.cronctl.core.TaskRegistry;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.filter.MethodsFilter;
import ru.syntezis.cronctl.filter.ScanModeFilter;
import ru.syntezis.cronctl.processor.ScheduledBeanProcessor;
import ru.syntezis.cronctl.properties.CronctlProperties;
import ru.syntezis.cronctl.util.condition.Conditions;
import ru.syntezis.cronctl.util.generator.BeanCreationRequest;
import ru.syntezis.cronctl.util.generator.BeanGenerator;
import ru.syntezis.cronctl.util.generator.GeneratedBeanDetails;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleAnnotationBeanPostProcessorTest {

    private static final BeanGenerator generator = new BeanGenerator();

    private TaskRegistry registry;
    private MethodsFilter methodsFilter;
    private ScanModeFilter scanModeFilter;
    private ScheduledBeanProcessor processor;

    private ScheduleAnnotationBeanPostProcessor underTest;

    @BeforeAll
    void init() {
        registry = new TaskRegistry();
        methodsFilter = new MethodsFilter();
        scanModeFilter = new ScanModeFilter(new CronctlProperties.Scan());
        processor = new ScheduledBeanProcessor();
        underTest = new ScheduleAnnotationBeanPostProcessor(registry, methodsFilter, scanModeFilter, processor);
    }

    @Test
    void postProcessAfterInitialization_ClassContainsScheduledMethod_TaskIsRegistered() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();

        // When
        underTest.postProcessAfterInitialization(bean, "test");

        // Then
        List<Task> tasks = registry.getAll();
        assertThat(tasks)
                .hasSize(1);

        Task task = tasks.getFirst();

        assertThat(task)
                .hasNoNullFieldsOrProperties()
                .extracting(Task::getDetails)
                .hasFieldOrPropertyWithValue("methodName", "doSomething")
                .extracting(ScheduledMethodDetails::getSchedule)
                .hasNoNullFieldsOrProperties()
                .hasFieldOrPropertyWithValue("fixedRate", 1000L);

        assertThat(task)
                .hasNoNullFieldsOrProperties()
                .extracting(Task::getReference)
                .hasFieldOrPropertyWithValue("method", bean.getClass().getDeclaredMethod("doSomething"))
                .hasFieldOrPropertyWithValue("bean", bean);
    }

    @ParameterizedTest
    @MethodSource("generatedBeansSource")
    void postProcessAfterInitialization_ClassContainsScheduledAndSimpleMethods_TasksAreRegisteredIfScheduledMethodsPresent(GeneratedBeanDetails details) {
        // Given
        String beanName = details.getBeanName();
        Object bean = details.getBean();
        int scheduledMethodsCount = details.getScheduledMethodsCount();

        // When
        underTest.postProcessAfterInitialization(bean, beanName);

        // Then
        List<Task> tasks = registry.getAll();

        assertThat(tasks)
                .hasSize(scheduledMethodsCount);

        assertThat(tasks)
                .extracting(Task::getDetails)
                .allSatisfy(d ->
                        assertThat(d)
                                .hasNoNullFieldsOrProperties()
                                .satisfies(new Conditions.ScheduledMethodDetailsUUIDCondition())
                                .satisfies(new Conditions.RandomGeneratedStringCondition())
                                .extracting(ScheduledMethodDetails::getSchedule)
                                .satisfies(new Conditions.FixedRateCondition())
                );

        assertThat(tasks)
                .extracting(Task::getReference)
                .allSatisfy(r ->
                        assertThat(r)
                                .hasNoNullFieldsOrProperties()
                                .hasFieldOrPropertyWithValue("bean", bean)
                                .satisfies(new Conditions.ScheduledMethodCondition())
                );
    }

    @AfterEach
    void cleanup() {
        registry.clear();
    }

    private static Stream<GeneratedBeanDetails> generatedBeansSource() {
        return Stream.of(
                generator.generate(BeanCreationRequest.of(1, 1)),
                generator.generate(BeanCreationRequest.of(1, 5)),
                generator.generate(BeanCreationRequest.of(0, 1)),
                generator.generate(BeanCreationRequest.of(5, 0)),
                generator.generate(BeanCreationRequest.of(0, 0)),
                generator.generate(BeanCreationRequest.of(99, 99)),
                generator.generate(BeanCreationRequest.of(0, 0))
        );
    }

    private static class SampleScheduledClass {

        @Scheduled(fixedRate = 1000L)
        public void doSomething() {
            throw new RuntimeException();
        }
    }
}