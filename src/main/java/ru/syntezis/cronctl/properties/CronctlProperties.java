package ru.syntezis.cronctl.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import ru.syntezis.cronctl.enums.ScanType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for cronctl, bound to the {@code cronctl.*} namespace.
 *
 * <p>All properties are optional. Default values are provided via
 * {@code META-INF/cronctl/cronctl-defaults.properties} and are designed
 * to work out of the box without any explicit configuration.
 *
 * <p>See {@code config-examples/} in the project root for ready-to-use configuration snippets.
 */
@Data
@ConfigurationProperties(prefix = "cronctl")
public class CronctlProperties {

    /** When {@code false}, no cronctl beans are registered and no endpoints are created. */
    private boolean enabled = true;

    @NestedConfigurationProperty
    private Executor executor = new Executor();

    @NestedConfigurationProperty
    private Scan scan = new Scan();

    @NestedConfigurationProperty
    private Api api = new Api();

    @NestedConfigurationProperty
    private Swagger swagger = new Swagger();

    @NestedConfigurationProperty
    private Ui ui = new Ui();

    @NestedConfigurationProperty
    private History history = new History();

    /**
     * Async executor configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Executor {

        /** Number of threads in the async execution pool. */
        private int threadPoolSize = 4;

        /** Maximum number of tasks that can wait in the submission queue. */
        private int queueCapacity = 100;

        /**
         * Default execution timeout in seconds. {@code 0} disables the timeout.
         * A task timeout of {@code 0} inherits this value, {@code -1} disables the timeout
         * for that task, and a positive value overrides this value.
         */
        private long timeoutSeconds = 60;

    }

    /**
     * Scheduled method discovery configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Scan {

        /**
         * Strategy used to discover {@code @Scheduled} methods.
         *
         * <ul>
         *   <li>{@code AUTO} — all methods, except those annotated with {@code @CronctlTask.Exclude}</li>
         *   <li>{@code ANNOTATED} — only methods annotated with {@code @CronctlTask}</li>
         *   <li>{@code PACKAGE} — only methods in packages listed in {@code base-packages}</li>
         * </ul>
         */
        private ScanType type = ScanType.AUTO;

        /**
         * Packages to scan when {@code type} is {@code PACKAGE}.
         * Subpackages are included automatically.
         */
        private List<String> basePackages = new ArrayList<>();

    }

    /**
     * API-related configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api {

        /** Base path for all cronctl REST endpoints. */
        private String basePath = "/api/cronctl";

        /** When {@code false}, authentication is required to access cronctl API endpoints. */
        private boolean publicAccess = true;

    }

    /**
     * Swagger UI integration configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Swagger {

        /** When {@code false}, authentication is required to access Swagger UI and API docs. */
        private boolean publicAccess = true;

        /** Group name displayed in Swagger UI when multiple API groups are present. */
        private String group = "cronctl";

        /** Ant-style path pattern used to include endpoints in the cronctl Swagger group. */
        private String pathsToMatch = "/api/cronctl/**";

    }

    /**
     * Operator UI configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Ui {

        /** When {@code false}, the cronctl operator UI is not exposed. */
        private boolean enabled = true;

    }

    /** Execution history configuration. */
    @Data
    @NoArgsConstructor
    public static class History {

        /** Maximum number of terminal records kept by the default in-memory store. */
        private int maxEntries = 1_000;

        /** Maximum age of terminal records kept by an execution store. */
        private Duration retention = Duration.ofDays(7);

        /** Interval between background deletion of expired terminal records. */
        private Duration cleanupInterval = Duration.ofMinutes(10);

        /** Explicit node identifier written to execution records. */
        @Nullable
        private String nodeId;

    }
}
