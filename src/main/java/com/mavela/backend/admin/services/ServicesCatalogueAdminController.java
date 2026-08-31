package com.mavela.backend.admin.services;

import com.mavela.backend.error.ApiErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Staff-only non-monetary catalogue configuration. Customer bearer tokens are
 * rejected by the dedicated admin security chain before these methods run.
 */
@RestController
@RequestMapping("/api/v1/admin/services/catalogue")
@Tag(
        name = "Administrator Services Catalogue Management",
        description = "Staff-only visibility, non-live availability, and display-order operations. These routes never create a purchase, debit, provider activation, bill lookup, loan, or remittance."
)
@SecurityRequirement(name = "adminBearerAuth")
public class ServicesCatalogueAdminController {

    private final ServicesCatalogueAdminService service;

    public ServicesCatalogueAdminController(
            ServicesCatalogueAdminService service
    ) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "Get the full staff services catalogue",
            description = "Requires services:read and returns safe operational metadata only. Internal notes, idempotency values, provider credentials, and payment data are never returned."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catalogue returned."),
            @ApiResponse(responseCode = "401", description = "A valid staff bearer token is required."),
            @ApiResponse(responseCode = "403", description = "The staff identity is inactive, unprovisioned, or lacks services:read.")
    })
    public ResponseEntity<List<AdminServiceCatalogueResponse>> getCatalogue(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getCatalogue(staffSubject(jwt)));
    }

    @GetMapping("/{serviceCode}")
    @Operation(
            summary = "Get safe staff configuration for one service",
            description = "Requires services:read. The response contains no provider credentials, customer data, or payment configuration."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service configuration returned."),
            @ApiResponse(responseCode = "404", description = "Service code was not found.")
    })
    public ResponseEntity<AdminServiceCatalogueResponse> getService(
            @PathVariable String serviceCode,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getService(staffSubject(jwt), serviceCode));
    }

    @PatchMapping("/{serviceCode}")
    @Operation(
            summary = "Update non-live service visibility, availability, and order",
            description = "Requires services:manage. AVAILABLE is rejected because provider and payment capabilities are not implemented. An idempotency key, controlled reason, and staff-only note are mandatory."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Configuration and immutable audit events recorded."),
            @ApiResponse(responseCode = "409", description = "Idempotency key was reused for a different request or a concurrent update conflicted."),
            @ApiResponse(responseCode = "422", description = "The requested non-live configuration is invalid or attempts to set AVAILABLE.")
    })
    public ResponseEntity<AdminServiceCatalogueResponse> updateService(
            @PathVariable String serviceCode,
            @Valid @RequestBody UpdateServiceCatalogueRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.updateService(
                        staffSubject(jwt), serviceCode, request
                ));
    }

    @GetMapping("/{serviceCode}/audit")
    @Operation(
            summary = "Get immutable safe service configuration audit history",
            description = "Requires services:read. Returns a capped page of operational change metadata; staff-only notes and idempotency values remain server-only."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Audit page returned."),
            @ApiResponse(responseCode = "404", description = "Service code was not found.")
    })
    public ResponseEntity<AdminServiceCatalogueAuditPageResponse> getAudit(
            @PathVariable String serviceCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getAudit(staffSubject(jwt), serviceCode, page, size));
    }

    private String staffSubject(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ServicesCatalogueAdminException(
                    ApiErrorCode.ADMIN_AUTHENTICATION_REQUIRED,
                    HttpStatus.UNAUTHORIZED
            );
        }
        return jwt.getSubject();
    }
}
