package ru.syntezis.cronctl.config;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Role;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import ru.syntezis.cronctl.bfpp.CronctlConfigurationContributor;
import ru.syntezis.cronctl.bpp.ScheduleAnnotationBeanPostProcessor;
import ru.syntezis.cronctl.config.security.CronctlSecurityConfiguration;
import ru.syntezis.cronctl.config.security.CronctlSwaggerSecurityConfiguration;
import ru.syntezis.cronctl.config.swagger.CronctlSwaggerConfiguration;
import ru.syntezis.cronctl.core.CompatibleTaskScheduler;
import ru.syntezis.cronctl.core.Cronctl;
import ru.syntezis.cronctl.core.NextExecutionTimeResolver;
import ru.syntezis.cronctl.core.ScheduledTaskNextExecutionResolver;
import ru.syntezis.cronctl.core.StateToggler;
import ru.syntezis.cronctl.core.TaskRegistry;
import ru.syntezis.cronctl.core.async.AsyncTaskExecutor;
import ru.syntezis.cronctl.core.execution.ExecutionHistoryCleanup;
import ru.syntezis.cronctl.core.execution.ExecutionLifecycleService;
import ru.syntezis.cronctl.core.execution.ExecutionStore;
import ru.syntezis.cronctl.core.execution.InMemoryExecutionStore;
import ru.syntezis.cronctl.core.execution.NodeIdProvider;
import ru.syntezis.cronctl.core.execution.PlannedExecutionTracker;
import ru.syntezis.cronctl.core.execution.RetryCoordinator;
import ru.syntezis.cronctl.core.execution.RetryLifecycleHandler;
import ru.syntezis.cronctl.core.execution.RetryPolicyEvaluator;
import ru.syntezis.cronctl.core.execution.RetryService;
import ru.syntezis.cronctl.core.execution.ScheduledExecutionObservationHandler;
import ru.syntezis.cronctl.core.execution.TaskConcurrencyController;
import ru.syntezis.cronctl.core.state.InMemoryTaskStateStore;
import ru.syntezis.cronctl.core.state.TaskStateRestorer;
import ru.syntezis.cronctl.core.state.TaskStateStore;
import ru.syntezis.cronctl.core.sync.BlockingTaskExecutor;
import ru.syntezis.cronctl.filter.MethodsFilter;
import ru.syntezis.cronctl.filter.ScanModeFilter;
import ru.syntezis.cronctl.presentation.controller.CronctlExceptionResolver;
import ru.syntezis.cronctl.processor.ScheduledBeanProcessor;
import ru.syntezis.cronctl.properties.CronctlProperties;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
    public static ScheduledBeanProcessor cronctlScheduledBeanProcessor(Environment environment) {
        return new ScheduledBeanProcessor(environment.getProperty("spring.application.name", "application"));
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
    public Cronctl cronctl(TaskRegistry registry) {
        return new Cronctl(registry);
    }

    /** Registers the resolver that computes the next execution times for cron-based tasks. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public NextExecutionTimeResolver cronctlNextExecutionTimeResolver(
            ObjectProvider<ScheduledTaskHolder> scheduledTaskHolderProvider,
            StateToggler stateToggler,
            PlannedExecutionTracker plannedExecutionTracker,
            ScheduledTaskNextExecutionResolver scheduledTaskNextExecutionResolver) {
        return new NextExecutionTimeResolver(scheduledTaskHolderProvider, stateToggler, plannedExecutionTracker,
                scheduledTaskNextExecutionResolver);
    }

    /** Resolves the live scheduler state without linking cronctl to a specific Spring 6.x minor release. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public ScheduledTaskNextExecutionResolver cronctlScheduledTaskNextExecutionResolver() {
        return new ScheduledTaskNextExecutionResolver(Clock.systemUTC());
    }

    /** Selects the application scheduler used to resume tasks, with a local fallback when none exists. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public CompatibleTaskScheduler cronctlCompatibleTaskScheduler(
            ObjectProvider<TaskScheduler> taskSchedulerProvider, BeanFactory beanFactory) {
        return new CompatibleTaskScheduler(taskSchedulerProvider, beanFactory);
    }

    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public StateToggler cronctlStateToggler(ObjectProvider<ScheduledTaskHolder> scheduledTaskHolderProvider,
                                            CompatibleTaskScheduler compatibleTaskScheduler,
                                            ExecutionLifecycleService lifecycleService,
                                            PlannedExecutionTracker plannedExecutionTracker,
                                            TaskStateStore taskStateStore) {
        return new StateToggler(scheduledTaskHolderProvider, compatibleTaskScheduler,
                lifecycleService, plannedExecutionTracker, taskStateStore);
    }

    /** Registers HTTP status mapping for cronctl task-management exceptions. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public CronctlExceptionResolver cronctlExceptionResolver() {
        CronctlExceptionResolver exceptionResolver = new CronctlExceptionResolver();
        exceptionResolver.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return exceptionResolver;
    }

    /** Serves the optional operator UI assets under the configured API base path. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    @ConditionalOnClass(name = "org.thymeleaf.spring6.SpringTemplateEngine")
    public CronctlUiWebMvcConfigurer cronctlUiWebMvcConfigurer(CronctlProperties properties) {
        return new CronctlUiWebMvcConfigurer(properties);
    }

    /** Registers the default bounded in-memory execution history store. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    @ConditionalOnMissingBean(ExecutionStore.class)
    public ExecutionStore cronctlExecutionStore(CronctlProperties properties) {
        return new InMemoryExecutionStore(properties.getHistory());
    }

    /** Registers periodic retention cleanup for the configured execution store. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public ExecutionHistoryCleanup cronctlExecutionHistoryCleanup(ExecutionStore executionStore,
                                                                  CronctlProperties properties) {
        return new ExecutionHistoryCleanup(executionStore, properties.getHistory());
    }

    /** Registers the process-local task pause store unless the application supplies a persistent implementation. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    @ConditionalOnMissingBean(TaskStateStore.class)
    public TaskStateStore cronctlTaskStateStore() {
        return new InMemoryTaskStateStore();
    }

    /** Restores persisted pause markers after Spring schedules are registered. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public TaskStateRestorer cronctlTaskStateRestorer(TaskRegistry taskRegistry,
                                                      StateToggler stateToggler,
                                                      TaskStateStore taskStateStore) {
        return new TaskStateRestorer(taskRegistry, stateToggler, taskStateStore);
    }

    /** Resolves the node identity attached to execution history records. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public NodeIdProvider cronctlNodeIdProvider(CronctlProperties properties, Environment environment) {
        String nodeId = Optional.ofNullable(properties.getHistory().getNodeId())
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .or(() -> Optional.ofNullable(environment.getProperty("spring.application.instance-id")))
                .or(() -> Optional.ofNullable(environment.getProperty("HOSTNAME")))
                .orElseGet(() -> environment.getProperty("spring.application.name", "application")
                        + ":" + UUID.randomUUID());

        return new NodeIdProvider(nodeId);
    }

    /** Registers process-local concurrency coordination shared by every execution source. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public TaskConcurrencyController cronctlTaskConcurrencyController() {
        return new TaskConcurrencyController();
    }

    /** Evaluates retry exception filters and bounded backoff delays. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public RetryPolicyEvaluator cronctlRetryPolicyEvaluator() {
        return new RetryPolicyEvaluator(() -> ThreadLocalRandom.current().nextDouble());
    }

    /** Coordinates delayed retry submissions and restores persistent queued retries. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public RetryCoordinator cronctlRetryCoordinator(
            ExecutionStore executionStore,
            TaskRegistry taskRegistry,
            RetryPolicyEvaluator retryPolicyEvaluator,
            ObjectProvider<ExecutionLifecycleService> lifecycleServiceProvider,
            ObjectProvider<AsyncTaskExecutor> asyncTaskExecutorProvider) {
        return new RetryCoordinator(
                executionStore, taskRegistry, retryPolicyEvaluator, lifecycleServiceProvider,
                asyncTaskExecutorProvider, Clock.systemUTC()
        );
    }

    /** Provides leaf-safe manual retry operations for the REST API and operator UI. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public RetryService cronctlRetryService(ExecutionStore executionStore,
                                            TaskRegistry taskRegistry,
                                            RetryCoordinator retryCoordinator) {
        return new RetryService(executionStore, taskRegistry, retryCoordinator);
    }

    /** Registers the common execution lifecycle shared by manual and automatic invocations. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public ExecutionLifecycleService cronctlExecutionLifecycleService(ExecutionStore executionStore,
                                                                      BlockingTaskExecutor blockingTaskExecutor,
                                                                      TaskConcurrencyController concurrencyController,
                                                                      RetryLifecycleHandler retryLifecycleHandler,
                                                                      NodeIdProvider nodeIdProvider) {
        return new ExecutionLifecycleService(executionStore, blockingTaskExecutor, concurrencyController,
                retryLifecycleHandler,
                Clock.systemUTC(), nodeIdProvider.getNodeId());
    }

    /** Tracks the planned slot used to calculate scheduled start delay. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public PlannedExecutionTracker cronctlPlannedExecutionTracker(
            TaskRegistry taskRegistry,
            ObjectProvider<ScheduledTaskHolder> scheduledTaskHolderProvider,
            ScheduledTaskNextExecutionResolver scheduledTaskNextExecutionResolver) {
        return new PlannedExecutionTracker(taskRegistry, scheduledTaskHolderProvider,
                scheduledTaskNextExecutionResolver);
    }

    /** Hooks Spring scheduled observations into cronctl execution history. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public ScheduledExecutionObservationHandler cronctlScheduledExecutionObservationHandler(
            TaskRegistry taskRegistry,
            ExecutionLifecycleService lifecycleService,
            PlannedExecutionTracker plannedExecutionTracker,
            TaskStateStore taskStateStore) {
        return new ScheduledExecutionObservationHandler(
                taskRegistry, lifecycleService, plannedExecutionTracker, taskStateStore
        );
    }

    /** Registers the async task executor with a bounded thread pool configured from {@code cronctl.executor.*}. */
    @Bean
    @Role(BeanDefinition.ROLE_SUPPORT)
    public AsyncTaskExecutor cronctlAsyncTaskExecutor(ExecutionLifecycleService lifecycleService,
                                                      CronctlProperties properties) {
        return new AsyncTaskExecutor(lifecycleService, properties.getExecutor());
    }
}
