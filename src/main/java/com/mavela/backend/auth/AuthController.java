package com.mavela.backend.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthLoginResponse> login(
            @Valid @RequestBody AuthLoginRequest request
    ) {
        return ResponseEntity.ok(
                authService.login(request)
        );
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthRefreshResponse> refresh(
            @Valid @RequestBody AuthRefreshRequest request
    ) {
        return ResponseEntity.ok(
                authService.refresh(request)
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody AuthLogoutRequest request
    ) {
        authService.logout(request);

        return ResponseEntity.noContent().build();
    }
}