package ru.syntezis.cronctl.bfpp;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import ru.syntezis.cronctl.CronctlConfiguration;
import ru.syntezis.cronctl.enums.ScanType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a user-declared {@link CronctlConfiguration} bean and writes its non-null fields
 * into the {@link org.springframework.core.env.Environment} at the highest priority before
 * any cronctl bean is instantiated.
 *
 * <p>This allows programmatic configuration to override {@code application.yml} and cronctl
 * defaults for all properties — including security and scan settings.
 */
@RequiredArgsConstructor
public class CronctlConfigurationContributor implements BeanFactoryPostProcessor {

    private final ConfigurableEnvironment environment;

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        String[] names = beanFactory.getBeanNamesForType(CronctlConfiguration.class, false, false);
        if (names.length == 0) {
            return;
        }

        CronctlConfiguration config = beanFactory.getBean(CronctlConfiguration.class);
        Map<String, Object> properties = toPropertyMap(config);

        if (properties.isEmpty()) {
            return;
        }

        environment.getPropertySources()
                .addFirst(new MapPropertySource("cronctlProgrammaticConfig", properties));
    }

    private Map<String, Object> toPropertyMap(CronctlConfiguration config) {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "cronctl.api.base-path", config.getBasePath());
        putIfPresent(map, "cronctl.api.public-access", config.getApiPublicAccess());
        putIfPresent(map, "cronctl.swagger.public-access", config.getSwaggerPublicAccess());
        putIfPresent(map, "cronctl.swagger.group", config.getSwaggerGroup());
        putIfPresent(map, "cronctl.swagger.paths-to-match", config.getSwaggerPathsToMatch());
        putIfPresent(map, "cronctl.scan.type", Optional.ofNullable(config.getScanType()).map(ScanType::name).orElse(null));
        putIfPresent(map, "cronctl.scan.base-packages", Optional.ofNullable(config.getScanBasePackages()).filter(p -> !p.isEmpty()).orElse(null));
        putIfPresent(map, "cronctl.executor.thread-pool-size", config.getExecutorThreadPoolSize());
        putIfPresent(map, "cronctl.executor.queue-capacity", config.getExecutorQueueCapacity());
        putIfPresent(map, "cronctl.executor.timeout-seconds", config.getExecutorTimeoutSeconds());
        return map;
    }

    private static void putIfPresent(Map<String, Object> map, String key, @Nullable Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
