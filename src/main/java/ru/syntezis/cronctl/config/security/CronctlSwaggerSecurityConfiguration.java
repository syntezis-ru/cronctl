package ru.syntezis.cronctl.config.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

public class CronctlSwaggerSecurityConfiguration {

    @Bean(name = "cronctlPublicSwaggerAccessSecurityFilterChain")
    @ConditionalOnProperty(value = "cronctl.swagger.public-access", havingValue = "true", matchIfMissing = true)
    @Order(SecurityProperties.BASIC_AUTH_ORDER - 1)
    public SecurityFilterChain cronctlPublicSwaggerAccessSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .build();
    }

    @Bean(name = "cronctlAuthorizedSwaggerAccessSecurityFilterChain")
    @ConditionalOnProperty(value = "cronctl.swagger.public-access", havingValue = "false")
    @Order(SecurityProperties.BASIC_AUTH_ORDER - 1)
    public SecurityFilterChain cronctlAuthorizedSwaggerAccessSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .build();
    }
}
