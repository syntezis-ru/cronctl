package ru.syntezis.cronctl.config.swagger;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

public class SwaggerCronctlSecurityConfiguration {

    @Bean(name = "cronctlPublicSwaggerAccessSecurityFilterChain")
    @ConditionalOnProperty(value = "cronctrl.swagger.public-access", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cronctlAuthorizedSwaggerAccessSecurityFilterChain")
    public SecurityFilterChain cronctlPublicSwaggerAccessSecurityFilterChain(HttpSecurity http) {
        return http.authorizeHttpRequests(authorize ->
                        authorize.requestMatchers("/swagger-ui/**", "/v3/api-docs*/**").permitAll()
                )
                .build();
    }

    @Bean(name = "cronctlAuthorizedSwaggerAccessSecurityFilterChain")
    @ConditionalOnProperty(value = "cronctrl.swagger.public-access", havingValue = "false")
    @ConditionalOnMissingBean(name = "cronctlPublicSwaggerAccessSecurityFilterChain")
    public SecurityFilterChain cronctlAuthorizedSwaggerAccessSecurityFilterChain(HttpSecurity http) {
        return http.authorizeHttpRequests(authorize ->
                        authorize.requestMatchers("/swagger-ui/**", "/v3/api-docs*/**").authenticated()
                )
                .build();
    }
}
