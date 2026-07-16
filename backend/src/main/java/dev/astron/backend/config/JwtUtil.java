package dev.astron.backend.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${astron.jwt.secret}")
    private String secret;

    @Value("${astron.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Creates a token that BOTH Spring Boot and Flask can validate,
    // because they share the same secret and both use HS256.
    public String generateToken(String email, String role, String userId) {
        return Jwts.builder()
            .subject(email)
            .claim("role", role)
            .claim("id", userId)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expirationMs))
            .signWith(key(), Jwts.SIG.HS256)
            .compact();
    }

    public Map<String, Object> validate(String token) {
        return Jwts.parser()
            .verifyWith(key())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
