package ru.syntezis.cronctl.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.*;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import ru.syntezis.cronctl.bfpp.CronctlConfigurationContributor;
import ru.syntezis.cronctl.bpp.ScheduleAnnotationBeanPostProcessor;
import ru.syntezis.cronctl.config.security.CronctlSecurityConfiguration;
import ru.syntezis.cronctl.config.security.CronctlSwaggerSecurityConfiguration;
import ru.syntezis.cronctl.config.swagger.CronctlSwaggerConfiguration;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.core.NextExecutionTimeResolver;
import ru.syntezis.cronctl.core.TaskRegistry;
import ru.syntezis.cronctl.core.async.AsyncTaskExecutor;
import ru.syntezis.cronctl.core.async.ExecutionRegistry;
import ru.syntezis.cronctl.core.sync.BlockingTaskExecutor;
import ru.syntezis.cronctl.filter.MethodsFilter;
import ru.syntezis.cronctl.filter.ScanModeFilter;
import ru.syntezis.cronctl.processor.ScheduledBeanProcessor;
import ru.syntezis.cronctl.properties.CronctlProperties;


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

    /** Registers the contributor that writes programmatic {@link ru.syntezis.cronctl.CronctlConfiguration} values into the environment. */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static BeanFactoryPostProcessor cronctlConfigurationContributor(ConfigurableEnvironment environment) {
        return new CronctlConfigurationContributor(environment);
    }

    /** Registers the BPP that scans beans for {@code @Scheduled} methods and populates the task registry. */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static ScheduleAnnotationBeanPostProcessor scheduleAnnotationBeanPostProcessor(
            TaskRegistry registry, MethodsFilter methodsFilter,
            ScanModeFilter scanModeFilter, ScheduledBeanProcessor processor) {
        return new ScheduleAnnotationBeanPostProcessor(registry, methodsFilter, scanModeFilter, processor);
    }

    /** Registers the filter that restricts which {@code @Scheduled} methods are eligible for registration. */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static MethodsFilter cronctlMethodsFilter() {
        return new MethodsFilter();
    }

    /** Registers the scan-mode filter that applies {@code AUTO}, {@code ANNOTATED}, or {@code PACKAGE} rules. */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static ScanModeFilter cronctlScanModeFilter(Environment environment) {
        CronctlProperties.Scan scan = Binder.get(environment)
                .bindOrCreate("cronctl.scan", CronctlProperties.Scan.class);
        return new ScanModeFilter(scan);
    }

    /** Registers the processor that extracts schedule metadata from annotated bean methods. */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static ScheduledBeanProcessor cronctlScheduledBeanProcessor() {
        return new ScheduledBeanProcessor();
    }

    /** Registers the in-memory registry that holds all discovered {@code @Scheduled} tasks. */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public static TaskRegistry cronctlTaskRegistry() {
        return new TaskRegistry();
    }

    /** Registers the synchronous task executor used for direct (blocking) task invocation. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public BlockingTaskExecutor cronctlTaskExecutor() {
        return new BlockingTaskExecutor();
    }

    /** Registers the main cronctl facade that exposes the public API for listing and executing tasks. */
    @Bean
    @Role(BeanDefinition.ROLE_APPLICATION)
    public Cronctl cronctl(TaskRegistry registry, BlockingTaskExecutor executor) {
        return new Cronctl(registry, executor);
    }

    /** Registers the resolver that computes next execution times for cron-based tasks. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public NextExecutionTimeResolver cronctlNextExecutionTimeResolver(
            ObjectProvider<ScheduledTaskHolder> scheduledTaskHolderProvider) {
        return new NextExecutionTimeResolver(scheduledTaskHolderProvider);
    }

    /** Registers the in-memory registry that tracks async execution state. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public ExecutionRegistry cronctlExecutionRegistry() {
        return new ExecutionRegistry();
    }

    /** Registers the async task executor with a bounded thread pool configured from {@code cronctl.executor.*}. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public AsyncTaskExecutor cronctlAsyncTaskExecutor(BlockingTaskExecutor syncExecutor,
                                                      ExecutionRegistry executionRegistry,
                                                      CronctlProperties properties) {
        return new AsyncTaskExecutor(syncExecutor, executionRegistry, properties.getExecutor());
    }
}
