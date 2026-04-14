package com.evelin.loganalysis.logcommon.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private boolean enabled = false;

    private String adminUsername;

    private String adminPasswordHash;

    private String jwtSecret;

    private int jwtTtlHours = 24;

    public boolean hasAdminCredentials() {
        return hasText(adminUsername) && hasText(adminPasswordHash);
    }

    public boolean hasJwtSecret() {
        return hasText(jwtSecret);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
