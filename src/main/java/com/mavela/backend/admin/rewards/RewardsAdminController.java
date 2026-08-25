package com.mavela.backend.admin.rewards;

import com.mavela.backend.error.ApiErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Isolated staff rewards API. The dedicated /api/v1/admin security chain
 * accepts only a configured staff Cognito access token; no customer token can
 * reach these operations.
 */
@RestController
@RequestMapping("/api/v1/admin/rewards")
@Tag(
        name = "Administrator Rewards Operations",
        description = "Staff-only immutable Mavela Gems adjustment and reversal operations. Gems are not cash and these routes never move customer funds."
)
@SecurityRequirement(name = "adminBearerAuth")
public class RewardsAdminController {

    private final RewardsAdminService rewardsAdminService;

    public RewardsAdminController(RewardsAdminService rewardsAdminService) {
        this.rewardsAdminService = rewardsAdminService;
    }

    @GetMapping("/customers")
    @Operation(
            summary = "Search customer Gems operations records",
            description = "Searches only username, name, or phone fields and returns minimal masked rewards triage data."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Capped customer page returned."),
            @ApiResponse(responseCode = "400", description = "Lookup query or pagination is invalid."),
            @ApiResponse(responseCode = "401", description = "A valid staff bearer token is required."),
            @ApiResponse(responseCode = "403", description = "The staff identity is inactive, unprovisioned, or lacks rewards:read.")
    })
    public ResponseEntity<AdminRewardsCustomerPageResponse> searchCustomers(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(rewardsAdminService.searchCustomers(
                staffSubject(jwt), query, page, size
        ));
    }

    @GetMapping("/customers/{customerId}/summary")
    @Operation(
            summary = "Get a customer Gems operations summary",
            description = "Returns staff-safe rewards and streak data only; staff internal notes are never included."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Summary returned."),
            @ApiResponse(responseCode = "404", description = "Customer is absent or inaccessible.")
    })
    public ResponseEntity<AdminRewardsCustomerSummaryResponse> getSummary(
            @PathVariable UUID customerId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(rewardsAdminService.getSummary(
                staffSubject(jwt), customerId
        ));
    }

    @GetMapping("/customers/{customerId}/activity")
    @Operation(
            summary = "Get immutable customer Gems activity",
            description = "Returns a capped staff-safe page of immutable ledger activity without internal staff notes or raw database identifiers."
    )
    public ResponseEntity<AdminRewardsActivityPageResponse> getActivity(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(rewardsAdminService.getActivity(
                staffSubject(jwt), customerId, page, size
        ));
    }

    @GetMapping("/customers/{customerId}/audit")
    @Operation(
            summary = "Get safe staff Gems adjustment and reversal audit history",
            description = "Returns action, amount, controlled reason, staff display name, and timestamp only. Internal notes and idempotency values never leave the backend."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Capped audit history returned."),
            @ApiResponse(responseCode = "404", description = "Customer is absent or inaccessible."),
            @ApiResponse(responseCode = "403", description = "The staff identity lacks rewards:read.")
    })
    public ResponseEntity<AdminRewardsAuditPageResponse> getAuditHistory(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(rewardsAdminService.getAuditHistory(
                staffSubject(jwt), customerId, page, size
        ));
    }

    @PostMapping("/customers/{customerId}/adjustments")
    @Operation(
            summary = "Create a manual immutable Gems adjustment",
            description = "Creates a single signed MANUAL_ADJUSTMENT ledger entry. An idempotency key, controlled reason, and staff-only note are mandatory."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Adjustment recorded and updated safe summary returned."),
            @ApiResponse(responseCode = "409", description = "The idempotency key has already been processed."),
            @ApiResponse(responseCode = "422", description = "Adjustment input or configured operational limit is invalid.")
    })
    public ResponseEntity<AdminRewardsCustomerSummaryResponse> createAdjustment(
            @PathVariable UUID customerId,
            @Valid @RequestBody CreateRewardsAdjustmentRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(rewardsAdminService.createAdjustment(
                staffSubject(jwt), customerId, request
        ));
    }

    @PostMapping("/customers/{customerId}/activity/{activityId}/reversals")
    @Operation(
            summary = "Create a compensating immutable Gems reversal",
            description = "Creates exactly one REVERSAL entry with the opposite amount for an eligible customer-scoped activity. The original ledger row is never edited or deleted."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reversal recorded and updated safe summary returned."),
            @ApiResponse(responseCode = "404", description = "Customer or activity is absent or inaccessible."),
            @ApiResponse(responseCode = "409", description = "Activity is not eligible, was already reversed, or the request was replayed."),
            @ApiResponse(responseCode = "422", description = "Reversal request is invalid.")
    })
    public ResponseEntity<AdminRewardsCustomerSummaryResponse> reverseActivity(
            @PathVariable UUID customerId,
            @PathVariable UUID activityId,
            @Valid @RequestBody CreateRewardsReversalRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(rewardsAdminService.reverseActivity(
                staffSubject(jwt), customerId, activityId, request
        ));
    }

    private String staffSubject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new RewardsAdminException(
                    ApiErrorCode.ADMIN_AUTHENTICATION_REQUIRED,
                    HttpStatus.UNAUTHORIZED
            );
        }
        return jwt.getSubject();
    }
}
