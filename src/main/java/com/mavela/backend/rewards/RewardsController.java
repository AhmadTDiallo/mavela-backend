package com.mavela.backend.rewards;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Customer-only rewards API. The JWT subject is the sole customer identity;
 * no endpoint accepts a customer ID from the client.
 */
@RestController
@RequestMapping("/api/v1/rewards")
@Tag(
        name = "Mavela Gems",
        description = "Authenticated customer-only non-monetary Gems and daily streak rewards."
)
@SecurityRequirement(name = "bearerAuth")
public class RewardsController {

    private final RewardsService rewardsService;

    public RewardsController(RewardsService rewardsService) {
        this.rewardsService = rewardsService;
    }

    @PostMapping("/daily-check-in")
    @Operation(
            summary = "Complete the current customer's daily Gems check-in",
            description = "Awards at most one server-authoritative daily check-in per Africa/Kinshasa calendar date. Repeating a completed check-in is idempotent."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check-in processed or already completed today."),
            @ApiResponse(responseCode = "401", description = "The bearer token is missing, invalid, or does not identify a customer.")
    })
    public ResponseEntity<DailyCheckInResponse> dailyCheckIn(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(rewardsService.claimDailyCheckIn(
                extractCustomerId(jwt)
        ));
    }

    @GetMapping("/summary")
    @Operation(
            summary = "Get the current customer's Mavela Gems summary",
            description = "Returns a ledger-derived available balance, Kinshasa-day streak state, next milestone, and at most five safe recent entries."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Gems summary returned."),
            @ApiResponse(responseCode = "401", description = "The bearer token is missing, invalid, or does not identify a customer.")
    })
    public ResponseEntity<GemsSummaryResponse> getSummary(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(rewardsService.getSummary(
                extractCustomerId(jwt)
        ));
    }

    @GetMapping("/activity")
    @Operation(
            summary = "Get paginated current-customer Mavela Gems activity",
            description = "Returns a customer-owned, capped page of safe immutable ledger activity. Page size is capped at 50."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Gems activity returned."),
            @ApiResponse(responseCode = "400", description = "The requested page is invalid."),
            @ApiResponse(responseCode = "401", description = "The bearer token is missing, invalid, or does not identify a customer.")
    })
    public ResponseEntity<GemsActivityPageResponse> getActivity(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(rewardsService.getActivity(
                extractCustomerId(jwt),
                page,
                size
        ));
    }

    private UUID extractCustomerId(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw invalidAccessTokenSubject();
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw invalidAccessTokenSubject();
        }
    }

    private ResponseStatusException invalidAccessTokenSubject() {
        return new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "INVALID_ACCESS_TOKEN_SUBJECT"
        );
    }
}
