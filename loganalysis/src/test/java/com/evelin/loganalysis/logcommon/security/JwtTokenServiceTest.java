package com.evelin.loganalysis.logcommon.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {

    @Test
    void tokenShouldBeValidInSameRuntime() {
        AuthProperties props = buildProps();
        JwtTokenService service = new JwtTokenService(props);

        String token = service.generateToken("admin");
        Claims claims = service.parseClaims(token);

        assertTrue(service.isCurrentRuntimeToken(claims));
    }

    @Test
    void tokenShouldBeInvalidAfterServiceRestart() {
        AuthProperties props = buildProps();
        JwtTokenService firstRuntime = new JwtTokenService(props);
        String token = firstRuntime.generateToken("admin");

        JwtTokenService secondRuntime = new JwtTokenService(props);
        Claims claimsInSecondRuntime = secondRuntime.parseClaims(token);

        assertFalse(secondRuntime.isCurrentRuntimeToken(claimsInSecondRuntime));
    }

    @Test
    void tokenShouldWorkWithoutConfiguredJwtSecret() {
        AuthProperties props = buildProps();
        props.setJwtSecret("");
        JwtTokenService service = new JwtTokenService(props);

        String token = service.generateToken("admin");
        Claims claims = service.parseClaims(token);

        assertNotNull(token);
        assertTrue(service.isCurrentRuntimeToken(claims));
        assertTrue("admin".equals(claims.getSubject()));
    }

    private AuthProperties buildProps() {
        AuthProperties props = new AuthProperties();
        props.setEnabled(true);
        props.setAdminUsername("admin");
        props.setAdminPasswordHash("$2y$10$dummy");
        props.setJwtSecret("test-jwt-secret-for-unit-test");
        props.setJwtTtlHours(24);
        return props;
    }
}
