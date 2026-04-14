package com.evelin.loganalysis.logcommon.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);
    private static final String CLAIM_STARTUP_ID = "startup_id";

    private final AuthProperties authProperties;
    private final String startupId;
    private final SecretKey signingKey;

    public JwtTokenService(AuthProperties authProperties) {
        this.authProperties = authProperties;
        this.startupId = UUID.randomUUID().toString();
        this.signingKey = buildSigningKey(resolveJwtSecret());
    }

    public String generateToken(String username) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofHours(Math.max(authProperties.getJwtTtlHours(), 1)));

        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_STARTUP_ID, startupId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isCurrentRuntimeToken(Claims claims) {
        if (claims == null) {
            return false;
        }
        String tokenStartupId = claims.get(CLAIM_STARTUP_ID, String.class);
        return startupId.equals(tokenStartupId);
    }

    private String resolveJwtSecret() {
        if (authProperties.hasJwtSecret()) {
            return authProperties.getJwtSecret().trim();
        }
        String generated = generateEphemeralSecret();
        log.warn("AUTH_JWT_SECRET is not configured, generated an ephemeral secret for current runtime");
        return generated;
    }

    private String generateEphemeralSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private SecretKey buildSigningKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] key = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(key);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
