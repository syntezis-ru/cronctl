package ru.syntezis.cronctl.processor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringValueResolver;
import ru.syntezis.cronctl.domain.ScheduledMethod;
import ru.syntezis.cronctl.domain.ScheduledMethodDetails;
import ru.syntezis.cronctl.domain.ScheduledMethodReference;
import ru.syntezis.cronctl.util.ScheduleUtils;

import java.lang.reflect.Method;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
public class ScheduledBeanProcessor {

    public ScheduledMethod process(Object bean, String beanName, Method scheduledMethod, StringValueResolver resolver) {
        UUID id = UUID.randomUUID();
        String methodName = scheduledMethod.getName();

        log.debug("Found scheduled method: {}, in class: {}, with bean name: {}. Adding to registry with id:{}",
                methodName, bean.getClass(), beanName, id);

        Scheduled annotation = scheduledMethod.getAnnotation(Scheduled.class);
        return ScheduledMethod.builder()
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
}
