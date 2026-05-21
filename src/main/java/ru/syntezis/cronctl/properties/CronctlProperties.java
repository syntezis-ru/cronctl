package ru.syntezis.cronctl.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

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
@ConfigurationProperties(prefix = "cronctl", ignoreUnknownFields = false)
public class CronctlProperties {

    /** When {@code false}, no cronctl beans are registered and no endpoints are created. */
    private boolean enabled = true;

    @NestedConfigurationProperty
    private Api api = new Api();

    @NestedConfigurationProperty
    private Swagger swagger = new Swagger();

    /**
     * API-related configuration.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api {

        /** Base path for all cronctl REST endpoints. */
        private String basePath = "/api/cronctl"; // NOSONAR

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
}
