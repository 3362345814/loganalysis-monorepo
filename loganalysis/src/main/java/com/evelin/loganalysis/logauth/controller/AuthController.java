package com.evelin.loganalysis.logauth.controller;

import com.evelin.loganalysis.logcommon.model.Result;
import com.evelin.loganalysis.logcommon.security.AuthProperties;
import com.evelin.loganalysis.logcommon.security.JwtTokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthProperties authProperties;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthController(AuthProperties authProperties,
                          PasswordEncoder passwordEncoder,
                          JwtTokenService jwtTokenService) {
        this.authProperties = authProperties;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    public ResponseEntity<Result<Map<String, Object>>> login(@Valid @RequestBody LoginRequest request) {
        if (!authProperties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Result.failed(400, "鉴权未启用"));
        }
        if (!authProperties.hasAdminCredentials() || !authProperties.hasJwtSecret()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Result.failed(500, "鉴权配置不完整，请先在 CLI 中配置管理员账户"));
        }

        if (!request.getUsername().equals(authProperties.getAdminUsername())
                || !passwordEncoder.matches(request.getPassword(), authProperties.getAdminPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.failed(401, "用户名或密码错误"));
        }

        String token = jwtTokenService.generateToken(request.getUsername());
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("tokenType", "Bearer");
        payload.put("expiresInSeconds", Math.max(authProperties.getJwtTtlHours(), 1) * 3600L);
        payload.put("username", request.getUsername());

        return ResponseEntity.ok(Result.success(payload));
    }

    @GetMapping("/me")
    public Result<Map<String, Object>> me(Authentication authentication) {
        if (!authProperties.isEnabled()) {
            return Result.failed(400, "鉴权未启用");
        }
        if (authentication == null) {
            return Result.failed(401, "未登录");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("username", authentication.getName());
        payload.put("role", "admin");
        return Result.success(payload);
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;
    }
}
