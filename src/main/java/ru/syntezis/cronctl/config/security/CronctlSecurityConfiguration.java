package ru.syntezis.cronctl.config.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import ru.syntezis.cronctl.properties.CronctlProperties;

public class CronctlSecurityConfiguration {

    private final String apiSecurityMatcherPattern;

    public CronctlSecurityConfiguration(CronctlProperties properties) {
        apiSecurityMatcherPattern = properties.getApi().getDefaultPath() + "/**";
    }

    @Bean(name = "cronctlPublicAccessSecurityFilterChain")
    @ConditionalOnProperty(value = "cronctl.security.enabled", havingValue = "false", matchIfMissing = true)
    @Order(SecurityFilterProperties.BASIC_AUTH_ORDER - 2)
    public SecurityFilterChain cronctlPublicSwaggerAccessSecurityFilterChain(HttpSecurity http) {
        return http
                .securityMatcher(apiSecurityMatcherPattern)
                .authorizeHttpRequests(authorize ->
                        authorize.anyRequest().permitAll()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean(name = "cronctlAuthorizedSwaggerAccessSecurityFilterChain")
    @ConditionalOnProperty(value = "cronctl.security.enabled", havingValue = "true")
    @Order(SecurityFilterProperties.BASIC_AUTH_ORDER - 2)
    public SecurityFilterChain cronctlAuthorizedSwaggerAccessSecurityFilterChain(HttpSecurity http) {
        return http
                .securityMatcher(apiSecurityMatcherPattern)
                .authorizeHttpRequests(authorize ->
                        authorize.anyRequest().authenticated()
                )
                .csrf(AbstractHttpConfigurer::disable)
                .build();
    }
}
