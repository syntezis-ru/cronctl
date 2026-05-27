package ru.syntezis.cronctl.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ru.syntezis.cronctl.annotation.CronctlTask;
import ru.syntezis.cronctl.properties.CronctlProperties;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

/**
 * Provides a scan-mode-specific {@link Predicate} for use in {@link MethodsFilter}.
 *
 * <ul>
 *   <li>{@code AUTO} — all methods pass; methods annotated with {@code @CronctlTask.Exclude} are skipped.</li>
 *   <li>{@code ANNOTATED} — only methods annotated with {@code @CronctlTask} pass.</li>
 *   <li>{@code PACKAGE} — only methods whose declaring class resides in one of the
 *       packages listed under {@code cronctl.scan.base-packages} pass.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class ScanModeFilter {

    private final CronctlProperties.Scan scanProperties;

    /**
     * @return predicate that implements the active scan-mode rules
     */
    public Predicate<Method> predicate() {
        return switch (scanProperties.getType()) {
            case AUTO -> this::notExcluded;
            case ANNOTATED -> m -> m.isAnnotationPresent(CronctlTask.class);
            case PACKAGE -> {
                List<String> basePackages = scanProperties.getBasePackages();
                if (basePackages.isEmpty()) {
                    log.warn("Scan type is PACKAGE but cronctl.scan.base-packages is empty — no methods will be registered");
                    yield m -> false;
                }
                yield m -> inPackage(m.getDeclaringClass(), basePackages) && notExcluded(m);
            }
        };
    }

    private boolean inPackage(Class<?> clazz, List<String> basePackages) {
        String className = clazz.getName();
        return basePackages.stream()
                .anyMatch(pkg -> className.startsWith(pkg + ".") || className.equals(pkg));
    }

    private boolean notExcluded(Method method) {
        return !method.isAnnotationPresent(CronctlTask.Exclude.class);
    }
}
