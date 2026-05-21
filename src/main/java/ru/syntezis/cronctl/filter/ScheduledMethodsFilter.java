package ru.syntezis.cronctl.filter;

import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Filters a list of methods, retaining only those annotated with {@code @Scheduled}.
 */
public class ScheduledMethodsFilter {

    /**
     * @param methods candidate methods to inspect
     * @return methods that have a {@code @Scheduled} annotation present
     */
    public List<Method> filter(List<Method> methods) {
        return methods.stream()
                .filter(m -> m.isAnnotationPresent(Scheduled.class))
                .toList();
    }
}
