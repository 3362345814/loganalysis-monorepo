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

    private String adminPassword;

    private String jwtSecret;

    private int jwtTtlHours = 24;

    public boolean hasAdminCredentials() {
        return hasText(adminUsername) && (hasAdminPasswordHash() || hasAdminPassword());
    }

    public boolean hasAdminPasswordHash() {
        return hasText(adminPasswordHash);
    }

    public boolean hasAdminPassword() {
        return hasText(adminPassword);
    }

    public boolean hasJwtSecret() {
        return hasText(jwtSecret);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
