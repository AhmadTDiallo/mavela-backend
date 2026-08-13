package com.mavela.backend.config;

import com.mavela.backend.admin.auth.AdminAccessDeniedHandler;
import com.mavela.backend.admin.auth.AdminAuthProperties;
import com.mavela.backend.admin.auth.AdminAuthenticationEntryPoint;
import com.mavela.backend.admin.auth.AdminAuthenticationUnavailableFilter;
import com.mavela.backend.admin.auth.AdminJwtAuthenticationConverter;
import com.mavela.backend.admin.auth.LazyAdminJwtDecoder;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AdminAuthProperties.class)
public class SecurityConfig {

    /**
     * Admin requests are always intercepted before the customer JWT chain.
     * When Cognito is not explicitly configured they receive a fail-closed
     * response and cannot authenticate with a customer access token.
     */
    @Bean
    @Order(1)
    SecurityFilterChain adminSecurityFilterChain(
            HttpSecurity http,
            AdminAuthProperties adminAuthProperties,
            @Qualifier("adminJwtDecoder") JwtDecoder adminJwtDecoder,
            AdminJwtAuthenticationConverter adminJwtAuthenticationConverter,
            AdminAuthenticationEntryPoint adminAuthenticationEntryPoint,
            AdminAccessDeniedHandler adminAccessDeniedHandler,
            com.mavela.backend.admin.auth.AdminProblemDetailWriter
                    adminProblemDetailWriter
    ) throws Exception {
        http
                .securityMatcher("/api/v1/admin/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(adminAuthenticationEntryPoint)
                        .accessDeniedHandler(adminAccessDeniedHandler));

        if (!adminAuthProperties.isEnabledAndConfigured()) {
            return http
                    .addFilterBefore(
                            new AdminAuthenticationUnavailableFilter(
                                    adminProblemDetailWriter
                            ),
                            AuthorizationFilter.class
                    )
                    .authorizeHttpRequests(authorize -> authorize
                            .anyRequest().denyAll())
                    .build();
        }

        return http
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(adminAuthenticationEntryPoint)
                        .accessDeniedHandler(adminAccessDeniedHandler)
                        .jwt(jwt -> jwt
                                .decoder(adminJwtDecoder)
                                .jwtAuthenticationConverter(
                                        adminJwtAuthenticationConverter
                                )
                        )
                )
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain customerSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("jwtDecoder") JwtDecoder customerJwtDecoder
    )
            throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR
                        ).permitAll()

                        .requestMatchers("/error").permitAll()

                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/customers"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/customers/*/phone-verification/otp"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/customers/*/phone-verification/otp/verify"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.PUT,
                                "/api/v1/customers/*/username"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/customers/*/pin"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout"
                        ).permitAll()

                        .requestMatchers(
                                "/actuator/health"
                        ).permitAll()

                        .anyRequest().authenticated()
                )

                .httpBasic(AbstractHttpConfigurer::disable)

                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(customerJwtDecoder))
                )

                .build();
    }

    @Bean(name = "adminJwtDecoder")
    @ConditionalOnMissingBean(name = "adminJwtDecoder")
    JwtDecoder adminJwtDecoder(
            AdminAuthProperties adminAuthProperties
    ) {
        return new LazyAdminJwtDecoder(adminAuthProperties);
    }
}
