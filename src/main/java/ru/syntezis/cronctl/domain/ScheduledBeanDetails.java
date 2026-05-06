package ru.syntezis.cronctl.domain;

import lombok.Builder;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.List;

@Data
@Builder
public class ScheduledBeanDetails {

    private String name;
    private ScheduleDetails schedule;
    private List<Method> annotatedMethods;
    private Object bean;
    private Class<?> type;

}
