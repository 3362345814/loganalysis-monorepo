package com.evelin.loganalysis.logauth.controller;

import com.evelin.loganalysis.logcommon.model.Result;
import com.evelin.loganalysis.logcommon.security.AuthProperties;
import com.evelin.loganalysis.logcommon.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthControllerTest {

    @Test
    void loginShouldSucceedWithPlainPasswordConfig() {
        AuthProperties props = buildPlainPasswordProps("admin");
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthController controller = new AuthController(props, encoder, new JwtTokenService(props));

        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin");

        ResponseEntity<Result<Map<String, Object>>> response = controller.login(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() != null && response.getBody().isSuccess());
    }

    @Test
    void loginShouldFailWithWrongPlainPassword() {
        AuthProperties props = buildPlainPasswordProps("admin");
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthController controller = new AuthController(props, encoder, new JwtTokenService(props));

        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong-password");

        ResponseEntity<Result<Map<String, Object>>> response = controller.login(request);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    private AuthProperties buildPlainPasswordProps(String password) {
        AuthProperties props = new AuthProperties();
        props.setEnabled(true);
        props.setAdminUsername("admin");
        props.setAdminPassword(password);
        props.setJwtSecret("auth-controller-test-secret");
        props.setJwtTtlHours(24);
        return props;
    }
}

