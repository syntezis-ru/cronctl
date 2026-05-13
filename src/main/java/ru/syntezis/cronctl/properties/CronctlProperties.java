package ru.syntezis.cronctl.properties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Data
@ConfigurationProperties(prefix = "cronctl", ignoreUnknownFields = false)
public class CronctlProperties {

    private boolean enabled = true;

    @NestedConfigurationProperty
    private Api api = new Api();

    @NestedConfigurationProperty
    private Swagger swagger = new Swagger();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api {

        private String basePath = "/api/cronctl"; // NOSONAR
        private boolean publicAccess = true;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Swagger {

        private boolean publicAccess = true;
        private String group = "cronctl";
        private String pathsToMatch = "/api/cronctl/**";

    }
}
