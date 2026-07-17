package ru.syntezis.cronctl.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringValueResolver;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.scheduled.ScheduledMethodReference;
import ru.syntezis.cronctl.domain.task.Task;
import ru.syntezis.cronctl.util.ScheduleUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds a fully-populated {@link Task} from a discovered {@code @Scheduled} method.
 */
@RequiredArgsConstructor
@Slf4j
public class ScheduledBeanProcessor {

    /**
     * Creates a {@link Task} capturing the method's identity, schedule configuration,
     * and a reference to the bean instance needed for execution.
     *
     * @param bean            the Spring bean that owns the method
     * @param beanName        name of the bean in the application context
     * @param scheduledMethod the reflective {@link Method} annotated with {@code @Scheduled}
     * @param resolver        resolver for property placeholders in annotation attributes; may be {@code null}
     * @return a fully-populated {@link Task}
     */
    public Task process(Object bean, String beanName, Method scheduledMethod, @Nullable StringValueResolver resolver) {
        UUID id = UUID.randomUUID();
        String methodName = scheduledMethod.getName();

        Optional<CronctlTask> taskAnnotation = Optional.ofNullable(scheduledMethod.getAnnotation(CronctlTask.class));

        log.debug("Found scheduled method: {}, in class: {}, with bean name: {}. Adding to registry with id:{}",
                methodName, bean.getClass(), beanName, id);

        String defaultDescription = scheduledMethod.getDeclaringClass().getSimpleName() + "." + methodName;

        Scheduled annotation = scheduledMethod.getAnnotation(Scheduled.class);
        return Task.builder()
                .label(taskAnnotation.map(CronctlTask::label)
                        .filter(s -> !s.isEmpty())
                        .orElse(methodName)
                )
                .description(taskAnnotation.map(CronctlTask::description)
                        .filter(s -> !s.isEmpty())
                        .orElse(defaultDescription)
                )
                .group(taskAnnotation.map(CronctlTask::group)
                        .filter(s -> !s.isEmpty())
                        .orElse("default")
                )
                .tags(taskAnnotation.map(t -> Arrays.stream(t.tags()).toList()).orElse(List.of()))
                .enabled(true)
                .togglingEnabled(taskAnnotation.map(CronctlTask::togglingEnabled).orElse(false))
                .timeoutSeconds(taskAnnotation.map(this::resolveTimeoutSeconds)
                        .orElse(CronctlTask.USE_GLOBAL_TIMEOUT))
                .details(ScheduledMethodDetails.builder()
                        .id(id)
                        .schedule(ScheduleUtils.assembleScheduleDetails(annotation, resolver))
                        .methodName(methodName)
                        .build()
                )
                .reference(
                        ScheduledMethodReference.builder()
                                .beanName(beanName)
                                .bean(bean)
                                .method(scheduledMethod)
                                .build()
                )
                .build();
    }

    private long resolveTimeoutSeconds(CronctlTask taskAnnotation) {
        long timeout = taskAnnotation.timeout();
        if (timeout < CronctlTask.NO_TIMEOUT) {
            throw new IllegalArgumentException("Cronctl task timeout must be -1, 0, or a positive value");
        }

        if (timeout == CronctlTask.NO_TIMEOUT || timeout == CronctlTask.USE_GLOBAL_TIMEOUT) {
            return timeout;
        }

        long timeoutSeconds = taskAnnotation.timeUnit().toSeconds(timeout);
        if (timeoutSeconds == CronctlTask.USE_GLOBAL_TIMEOUT) {
            throw new IllegalArgumentException("Cronctl task timeout must be at least one second");
        }

        return timeoutSeconds;
    }
}
