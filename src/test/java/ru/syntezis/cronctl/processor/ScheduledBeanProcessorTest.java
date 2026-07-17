package ru.syntezis.cronctl.processor;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringValueResolver;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.domain.task.Task;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScheduledBeanProcessorTest {

    private final ScheduledBeanProcessor underTest = new ScheduledBeanProcessor();

    @Test
    void process_MethodWithFullCronctlTask_TaskPopulatedFromAnnotation() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("fullyCronctlTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getLabel())
                .isEqualTo("My Label");
        assertThat(actual.getDescription())
                .isEqualTo("My Description");
        assertThat(actual.getGroup())
                .isEqualTo("my-group");
        assertThat(actual.getTags())
                .containsExactly("tag1", "tag2");
        assertThat(actual.isEnabled())
                .isTrue();
        assertThat(actual.isTogglingEnabled())
                .isTrue();
    }

    @Test
    void process_MethodWithoutCronctlTask_DefaultLabelIsMethodName() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getLabel())
                .isEqualTo("notTagged");
    }

    @Test
    void process_MethodWithoutCronctlTask_DefaultDescriptionIsClassDotMethod() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getDescription())
                .isEqualTo("SampleScheduledClass.notTagged");
    }

    @Test
    void process_MethodWithoutCronctlTask_DefaultGroupAndEmptyTags() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getGroup())
                .isEqualTo("default");
        assertThat(actual.getTags())
                .isEmpty();
        assertThat(actual.isEnabled())
                .isTrue();
        assertThat(actual.isTogglingEnabled())
                .isFalse();
    }

    @Test
    void process_MethodWithEmptyCronctlTaskFields_LabelFallsBackToMethodName() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("emptyCronctlTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getLabel())
                .isEqualTo("emptyCronctlTagged");
    }

    @Test
    void process_MethodWithEmptyCronctlTaskFields_DescriptionFallsBackToDefault() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("emptyCronctlTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getDescription())
                .isEqualTo("SampleScheduledClass.emptyCronctlTagged");
    }

    @Test
    void process_MethodWithCronctlTaskPartialFields_UnsetFieldsFallToDefaults() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("partialCronctlTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getLabel())
                .isEqualTo("custom-label");
        assertThat(actual.getDescription())
                .isEqualTo("SampleScheduledClass.partialCronctlTagged");
        assertThat(actual.getGroup())
                .isEqualTo("default");
    }

    @Test
    void process_WithStringValueResolver_CronPlaceholderResolved() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("withCronPlaceholder");
        StringValueResolver resolver = value -> value.equals("${schedule.cron}") ? "0 0 * * * *" : value;

        // When
        final Task actual = underTest.process(bean, "testBean", method, resolver);

        // Then
        assertThat(actual.getDetails().getSchedule().getCron())
                .isEqualTo("0 0 * * * *");
    }

    @Test
    void process_CalledTwice_TasksHaveUniqueIds() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final Task task1 = underTest.process(bean, "testBean", method, null);
        final Task task2 = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(task1.getId())
                .isNotEqualTo(task2.getId());
    }

    @Test
    void process_TaskReferencePopulatedCorrectly() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final Task actual = underTest.process(bean, "myBean", method, null);

        // Then
        assertThat(actual.getReference().getBean())
                .isSameAs(bean);
        assertThat(actual.getReference().getBeanName())
                .isEqualTo("myBean");
        assertThat(actual.getReference().getMethod())
                .isEqualTo(method);
    }

    @Test
    void process_TaskDetailsMethodNameAndIdPopulated() throws NoSuchMethodException {
        // Given
        SampleScheduledClass bean = new SampleScheduledClass();
        Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final Task actual = underTest.process(bean, "testBean", method, null);

        // Then
        assertThat(actual.getDetails().getMethodName())
                .isEqualTo("notTagged");
        assertThat(actual.getDetails().getId())
                .isNotNull();
        assertThat(actual.getDetails().getSchedule())
                .isNotNull();
    }

    @Test
    void process_MethodWithoutCronctlTask_GlobalTimeoutInherited() throws NoSuchMethodException {
        // Given
        final long expected = CronctlTask.USE_GLOBAL_TIMEOUT;
        final SampleScheduledClass bean = new SampleScheduledClass();
        final Method method = SampleScheduledClass.class.getDeclaredMethod("notTagged");

        // When
        final Task task = underTest.process(bean, "testBean", method, null);
        final long actual = task.getTimeoutSeconds();

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void process_MethodWithDefaultCronctlTask_GlobalTimeoutInherited() throws NoSuchMethodException {
        // Given
        final long expected = CronctlTask.USE_GLOBAL_TIMEOUT;
        final SampleScheduledClass bean = new SampleScheduledClass();
        final Method method = SampleScheduledClass.class.getDeclaredMethod("emptyCronctlTagged");

        // When
        final Task task = underTest.process(bean, "testBean", method, null);
        final long actual = task.getTimeoutSeconds();

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void process_MethodWithNoTimeout_TimeoutDisabled() throws NoSuchMethodException {
        // Given
        final long expected = CronctlTask.NO_TIMEOUT;
        final SampleScheduledClass bean = new SampleScheduledClass();
        final Method method = SampleScheduledClass.class.getDeclaredMethod("withoutTimeout");

        // When
        final Task task = underTest.process(bean, "testBean", method, null);
        final long actual = task.getTimeoutSeconds();

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void process_MethodWithTaskTimeout_TimeoutConvertedToSeconds() throws NoSuchMethodException {
        // Given
        final long expected = 120L;
        final SampleScheduledClass bean = new SampleScheduledClass();
        final Method method = SampleScheduledClass.class.getDeclaredMethod("withTaskTimeout");

        // When
        final Task task = underTest.process(bean, "testBean", method, null);
        final long actual = task.getTimeoutSeconds();

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void process_MethodWithInvalidNegativeTimeout_IllegalArgumentException() throws NoSuchMethodException {
        // Given
        final SampleScheduledClass bean = new SampleScheduledClass();
        final Method method = SampleScheduledClass.class.getDeclaredMethod("withInvalidNegativeTimeout");

        // When
        final ThrowingCallable actual = () -> underTest.process(bean, "testBean", method, null);

        // Then
        assertThatThrownBy(actual)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Cronctl task timeout must be -1, 0, or a positive value");
    }

    private static class SampleScheduledClass {

        @CronctlTask(
                label = "My Label",
                description = "My Description",
                group = "my-group",
                tags = {"tag1", "tag2"},
                togglingEnabled = true
        )
        @Scheduled(fixedRate = 1000L)
        public void fullyCronctlTagged() {}

        @Scheduled(fixedRate = 1000L)
        public void notTagged() {}

        @CronctlTask
        @Scheduled(fixedRate = 1000L)
        public void emptyCronctlTagged() {}

        @Scheduled(cron = "${schedule.cron}")
        public void withCronPlaceholder() {}

        @CronctlTask(label = "custom-label")
        @Scheduled(fixedRate = 1000L)
        public void partialCronctlTagged() {}

        @CronctlTask(timeout = CronctlTask.NO_TIMEOUT)
        @Scheduled(fixedRate = 1000L)
        public void withoutTimeout() {}

        @CronctlTask(timeout = 2L, timeUnit = TimeUnit.MINUTES)
        @Scheduled(fixedRate = 1000L)
        public void withTaskTimeout() {}

        @CronctlTask(timeout = -2L)
        @Scheduled(fixedRate = 1000L)
        public void withInvalidNegativeTimeout() {}

    }
}
