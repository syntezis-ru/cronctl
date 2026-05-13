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
    private Security security = new Security();

    @NestedConfigurationProperty
    private Swagger swagger = new Swagger();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Api {

        private String defaultPath = "/api/cronctl"; // NOSONAR

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Security {

        private boolean enabled = false;

    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Swagger {

        private boolean enabled = true;
        private boolean publicAccess = true;
        private String group = "cronctl";
        private String pathsToMatch = "/api/cronctl/**";

    }
}
