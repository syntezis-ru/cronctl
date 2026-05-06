package ru.syntezis.cronctl.bpp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.scheduling.annotation.Scheduled;
import ru.syntezis.cronctl.domain.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.ScheduledMethodReference;
import ru.syntezis.cronctl.scan.TaskRegistry;
import ru.syntezis.cronctl.util.ScheduleUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
public class ScheduleAnnotationBeanPostProcessor implements BeanPostProcessor {

    private final TaskRegistry registry;

    @Override
    public @Nullable Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        log.debug("Processing bean with name: {}", beanName);
        List<Method> methods = Arrays.stream(bean.getClass().getMethods())
                .filter(m -> m.isAnnotationPresent(Scheduled.class))
                .toList();

        if (methods.isEmpty()) {
            log.debug("No scheduled methods found for bean: {}", beanName);
            return bean;
        }

        methods.forEach(m -> {
            UUID id = UUID.randomUUID();
            log.debug("Found scheduled method: {} in class: {}. Adding to registry with id:{}", m.getName(), bean.getClass(), id);
            Scheduled annotation = m.getAnnotation(Scheduled.class);
            registry.add(ScheduledMethodDetails.builder()
                            .id(id)
                            .schedule(ScheduleUtils.assembleScheduleDetails(annotation))
                            .methodName(m.getName())
                            .build(),
                    ScheduledMethodReference.builder()
                            .bean(bean)
                            .method(m)
                            .build()
            );
        });

        log.debug("Finished processing bean: {}", beanName);
        return bean;
    }
}
