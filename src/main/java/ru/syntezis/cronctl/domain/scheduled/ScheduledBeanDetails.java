package ru.syntezis.cronctl.domain.scheduled;

import lombok.Builder;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Transient snapshot of a Spring bean and all its {@code @Scheduled} methods collected
 * during bean post-processing, before individual {@link ru.syntezis.cronctl.domain.task.Task} entries are built.
 */
@Data
@Builder
public class ScheduledBeanDetails {

    private String name;
    private ScheduleDetails schedule;
    private List<Method> annotatedMethods;
    private Object bean;
    private Class<?> type;

}
