package ru.syntezis.cronctl.filter;

import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

/**
 * Filters a list of methods by {@code @Scheduled} presence plus an additional predicate.
 *
 * <p>The {@code @Scheduled} check is always applied first. The caller supplies the
 * scan-mode-specific predicate — typically obtained from {@link ScanModeFilter#predicate()}.
 */
public class MethodsFilter {

    /**
     * Returns all methods from {@code methods} that carry {@code @Scheduled} and satisfy {@code predicate}.
     *
     * @param methods   candidate methods, typically all declared methods of a bean class
     * @param predicate scan-mode predicate supplied by {@link ScanModeFilter#predicate()}
     * @return filtered list; never {@code null}
     */
    public List<Method> filter(List<Method> methods, Predicate<Method> predicate) {
        return methods.stream()
                .filter(m -> m.isAnnotationPresent(Scheduled.class))
                .filter(predicate)
                .toList();
    }
}
