package ru.syntezis.cronctl.filter;

import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.List;

public class ScheduledMethodsFilter {

    public List<Method> filter(List<Method> methods) {
        return methods.stream()
                .filter(m -> m.isAnnotationPresent(Scheduled.class))
                .toList();
    }
}
