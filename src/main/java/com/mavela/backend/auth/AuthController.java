package com.mavela.backend.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(
        name = "Authentication",
        description = "Session creation, refresh, and revocation."
)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Sign in with phone number and PIN",
            description = "Issues an access token and refresh token for an active customer."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Authentication succeeded and tokens were issued."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The sign-in request is invalid."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "The phone number or PIN is invalid."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "The customer's account is not permitted to authenticate."
            ),
            @ApiResponse(
                    responseCode = "423",
                    description = "PIN authentication is temporarily locked."
            )
    })
    public ResponseEntity<AuthLoginResponse> login(
            @Valid @RequestBody AuthLoginRequest request
    ) {
        return ResponseEntity.ok(
                authService.login(request)
        );
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Refresh an authenticated session",
            description = "Exchanges a valid refresh token for a new access token and refresh token."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Tokens refreshed successfully."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The refresh request is invalid."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "The refresh token is invalid, expired, revoked, or reused."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "The customer's account is not permitted to authenticate."
            )
    })
    public ResponseEntity<AuthRefreshResponse> refresh(
            @Valid @RequestBody AuthRefreshRequest request
    ) {
        return ResponseEntity.ok(
                authService.refresh(request)
        );
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Sign out a session",
            description = "Revokes the refresh-token family when the supplied token is known."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "Sign-out completed."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The sign-out request is invalid."
            )
    })
    public ResponseEntity<Void> logout(
            @Valid @RequestBody AuthLogoutRequest request
    ) {
        authService.logout(request);

        return ResponseEntity.noContent().build();
    }
}
