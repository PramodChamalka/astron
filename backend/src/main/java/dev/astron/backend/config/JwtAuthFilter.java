package dev.astron.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

// OncePerRequestFilter guarantees this code runs exactly once per
// incoming request, before it reaches our @RestController methods.
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain)
            throws ServletException, IOException {

        // Login and register must always work with NO token, so we
        // skip JWT checking entirely for anything under /api/auth/.
        String path = request.getRequestURI();
        if (path.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. Read the Authorization header
        String authHeader = request.getHeader("Authorization");

        // 2. Only bother if it looks like "Bearer <token>"
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring("Bearer ".length());

            try {
                // 3. Validate the token (throws if it's expired/fake/broken)
                Map<String, Object> claims = jwtUtil.validate(token);

                // 4. Pull the role out of the token's payload
                String role = (String) claims.get("role");

                // 5. Spring Security's hasRole("Admin") secretly looks
                // for an authority literally named "ROLE_Admin", so we
                // must add that prefix ourselves here.
                List<SimpleGrantedAuthority> authorities =
                    List.of(new SimpleGrantedAuthority("ROLE_" + role));

                // "sub" is the standard JWT claim holding who the token
                // belongs to - in our case, the user's email.
                Authentication authentication =
                    new UsernamePasswordAuthenticationToken(
                        claims.get("sub"), null, authorities);

                // Registering this Authentication is what makes Spring
                // Security treat the rest of this request as "logged in".
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // 6. Missing/expired/invalid token - do nothing here.
                // We simply continue without authenticating; it's up to
                // SecurityConfig's rules to decide if that's allowed
                // (e.g. a public endpoint) or rejected (401/403).
            }
        }

        // Always let the request continue to the next filter/controller.
        filterChain.doFilter(request, response);
    }
}
