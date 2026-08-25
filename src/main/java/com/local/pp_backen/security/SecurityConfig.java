package com.local.pp_backen.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final String allowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          @Value("${app.cors.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(unauthorizedEntryPoint())
                    .accessDeniedHandler(accessDeniedHandler()))
            .authorizeHttpRequests(auth -> auth
                // Preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Reading your own session needs a token; the rest of /api/auth is public
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/auth/**").permitAll()

                // Figures and page renders are embedded in quiz pages
                .requestMatchers(HttpMethod.GET, "/api/media/**").permitAll()

                // The whole admin console
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Browsing the exam / paper catalogue is public so the landing page works
                // for signed-out visitors. Question content stays behind a login.
                .requestMatchers(HttpMethod.GET, "/api/exams", "/api/exams/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/subjects", "/api/subjects/**").permitAll()

                // Admin-only content management
                .requestMatchers(HttpMethod.POST, "/api/exams/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/exams/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/exams/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/subjects/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/subjects/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/subjects/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/questions/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/questions/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/questions/**").hasRole("ADMIN")

                // Everything else (questions, quiz attempts) requires a signed-in user
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** No usable token: 401, so the frontend knows to send the user to the login page. */
    @Bean
    public AuthenticationEntryPoint unauthorizedEntryPoint() {
        return (request, response, authException) ->
                writeError(response, 401, "Unauthorized", "Sign in to continue");
    }

    /**
     * Signed in, but not allowed here: 403. This has to be explicit — without it a member
     * hitting an admin URL gets a 401, which the frontend reads as an expired session and
     * signs them out for no reason.
     */
    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, deniedException) ->
                writeError(response, 403, "Forbidden",
                        "Your account does not have access to this area.");
    }

    private static void writeError(HttpServletResponse response, int status,
                                   String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(new ObjectMapper().writeValueAsString(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status,
                "error", error,
                "message", message)));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
