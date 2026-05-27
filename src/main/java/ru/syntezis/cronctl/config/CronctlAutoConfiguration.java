package ru.syntezis.cronctl.config;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.*;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import ru.syntezis.cronctl.CronctlConfiguration;
import ru.syntezis.cronctl.bpp.ScheduleAnnotationBeanPostProcessor;
import ru.syntezis.cronctl.config.security.CronctlSecurityConfiguration;
import ru.syntezis.cronctl.config.security.CronctlSwaggerSecurityConfiguration;
import ru.syntezis.cronctl.config.swagger.CronctlSwaggerConfiguration;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.core.TaskRegistry;
import ru.syntezis.cronctl.core.async.AsyncTaskExecutor;
import ru.syntezis.cronctl.core.async.ExecutionRegistry;
import ru.syntezis.cronctl.core.sync.BlockingTaskExecutor;
import ru.syntezis.cronctl.enums.ScanType;
import ru.syntezis.cronctl.filter.MethodsFilter;
import ru.syntezis.cronctl.filter.ScanModeFilter;
import ru.syntezis.cronctl.processor.ScheduledBeanProcessor;
import ru.syntezis.cronctl.properties.CronctlProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Spring Boot autoconfiguration entry point for cronctl.
 *
 * <p>Activated automatically via {@code AutoConfiguration.imports} when {@code cronctl.enabled}
 * is {@code true} (default). Registers all infrastructure beans required for {@code @Scheduled}
 * method discovery, execution, and REST API exposure.
 *
 * <p>To override the default security, declare a custom {@link CronctlSecurityConfiguration}
 * bean in the application context.
 */
@AutoConfiguration
@ConditionalOnProperty(value = "cronctl.enabled", matchIfMissing = true)
@EnableConfigurationProperties(CronctlProperties.class)
@Import({
        CronctlSecurityConfiguration.class,
        CronctlSwaggerSecurityConfiguration.class,
        CronctlSwaggerConfiguration.class
})
@ComponentScan({
        "ru.syntezis.cronctl.presentation.mapper",
        "ru.syntezis.cronctl.presentation.controller"
})
@PropertySource("classpath:META-INF/cronctl/cronctl-defaults.properties")
public class CronctlAutoConfiguration {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static BeanFactoryPostProcessor cronctlConfigurationContributor(ConfigurableEnvironment environment) {
        return new CronctlConfigurationContributor(environment);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static ScheduleAnnotationBeanPostProcessor scheduleAnnotationBeanPostProcessor(
            TaskRegistry registry, MethodsFilter methodsFilter,
            ScanModeFilter scanModeFilter, ScheduledBeanProcessor processor) {
        return new ScheduleAnnotationBeanPostProcessor(registry, methodsFilter, scanModeFilter, processor);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static MethodsFilter cronctlMethodsFilter() {
        return new MethodsFilter();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static ScanModeFilter cronctlScanModeFilter(Environment environment) {
        CronctlProperties.Scan scan = Binder.get(environment)
                .bindOrCreate("cronctl.scan", CronctlProperties.Scan.class);
        return new ScanModeFilter(scan);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static ScheduledBeanProcessor cronctlScheduledBeanProcessor() {
        return new ScheduledBeanProcessor();
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static TaskRegistry cronctlTaskRegistry() {
        return new TaskRegistry();
    }

    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public BlockingTaskExecutor cronctlTaskExecutor() {
        return new BlockingTaskExecutor();
    }

    @Bean
    @Role(BeanDefinition.ROLE_APPLICATION)
    public Cronctl cronctl(TaskRegistry registry, BlockingTaskExecutor executor) {
        return new Cronctl(registry, executor);
    }

    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public ExecutionRegistry cronctlExecutionRegistry() {
        return new ExecutionRegistry();
    }

    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public AsyncTaskExecutor cronctlAsyncTaskExecutor(BlockingTaskExecutor syncExecutor,
                                                      ExecutionRegistry executionRegistry,
                                                      CronctlProperties properties) {
        return new AsyncTaskExecutor(syncExecutor, executionRegistry, properties.getExecutor());
    }

    /**
     * Reads a user-declared {@link CronctlConfiguration} bean and writes its non-null fields
     * into the {@link Environment} at the highest priority before any cronctl bean is instantiated.
     *
     * <p>This allows programmatic configuration to override {@code application.yml} and cronctl
     * defaults for all properties — including security and scan settings.
     */
    @RequiredArgsConstructor
    private static class CronctlConfigurationContributor implements BeanFactoryPostProcessor {

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
}
