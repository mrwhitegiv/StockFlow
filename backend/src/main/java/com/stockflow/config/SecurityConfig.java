package com.stockflow.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;

@Configuration
public class SecurityConfig {
    // Temporary local-development API surface. Authentication/RBAC is a later issue.
    private static final String[] MASTER_DATA = {
            "/api/categories", "/api/categories/*", "/api/products", "/api/products/*",
            "/api/products/*/enabled", "/api/products/*/skus", "/api/skus/*",
            "/api/warehouses", "/api/warehouses/*"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                // These JSON endpoints do not use cookies, HTTP Basic or any ambient credentials.
                // Revisit this scoped exemption when authentication is implemented.
                .csrf(csrf -> csrf.ignoringRequestMatchers(MASTER_DATA)
                        .ignoringRequestMatchers("/api/purchase-orders", "/api/purchase-orders/**"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/inventory", "/api/inventory/*").permitAll()
                        .requestMatchers(MASTER_DATA).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/purchase-orders", "/api/purchase-orders/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/purchase-orders", "/api/purchase-orders/*/items",
                                "/api/purchase-orders/*/approve", "/api/purchase-orders/*/receive",
                                "/api/purchase-orders/*/complete", "/api/purchase-orders/*/cancel").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/purchase-orders/*/items/*").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/purchase-orders/*/items/*").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> forbidden(response))
                        .accessDeniedHandler((request, response, exception) -> forbidden(response)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    private void forbidden(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":403,\"message\":\"Forbidden\",\"data\":null}");
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim).filter(origin -> !origin.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Accept", "Content-Type"));
        config.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration inventory = new CorsConfiguration(config);
        inventory.setAllowedMethods(List.of("GET"));
        source.registerCorsConfiguration("/api/inventory", inventory);
        source.registerCorsConfiguration("/api/inventory/**", inventory);
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
