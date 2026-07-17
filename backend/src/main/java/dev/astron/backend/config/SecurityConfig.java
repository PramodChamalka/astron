package dev.astron.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpStatus;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http)
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsSource()))
            // We use JWTs, not server-side sessions, so tell Spring
            // Security to never create or use an HttpSession.
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // By default, Spring Security can't tell "no token at all"
            // apart from "wrong role" and returns 403 for both. This
            // makes a missing/invalid token return 401 instead, while
            // a valid-but-wrong-role request still gets 403 as normal.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                // Spring Boot internally forwards to /error to render
                // JSON error bodies (e.g. after an AccessDeniedException
                // triggers response.sendError()). That forwarded request
                // must not itself get blocked, or it silently overwrites
                // the real status code with a 401 from denyAll() below.
                .requestMatchers("/error").permitAll()
                // Login and register must stay open with no token.
                .requestMatchers("/api/auth/**").permitAll()
                // Only Admins can manage users.
                .requestMatchers("/api/users/**").hasRole("Admin")
                // Only Admins/Managers can create assignments or tasks.
                .requestMatchers(HttpMethod.POST, "/api/assignments/**")
                    .hasAnyRole("Admin", "Manager")
                .requestMatchers(HttpMethod.POST, "/api/tasks/**")
                    .hasAnyRole("Admin", "Manager")
                // Everything else under /api/ just needs a valid token.
                .requestMatchers("/api/**").authenticated()
                // Anything not covered above is refused outright.
                .anyRequest().denyAll()
            )
            // Run our JWT check before Spring's own username/password
            // filter, so the Authentication is already set by the time
            // the authorizeHttpRequests rules above are evaluated.
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
