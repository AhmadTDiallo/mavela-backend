package com.mavela.backend.services.catalogue;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Customer-only service metadata. This controller has no purchase, debit,
 * lookup, loan, or remittance command endpoints.
 */
@RestController
@RequestMapping("/api/v1/services")
@Tag(
        name = "Services catalogue",
        description = "Authenticated customer-only non-live service availability metadata."
)
@SecurityRequirement(name = "bearerAuth")
public class ServicesCatalogueController {

    private final ServicesCatalogueService servicesCatalogueService;

    public ServicesCatalogueController(
            ServicesCatalogueService servicesCatalogueService
    ) {
        this.servicesCatalogueService = servicesCatalogueService;
    }

    @GetMapping("/catalog")
    @Operation(
            summary = "Get the Mavela services catalogue",
            description = "Returns stable, customer-safe service metadata only. This endpoint does not initiate a purchase or provider lookup."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catalogue returned."),
            @ApiResponse(responseCode = "401", description = "The bearer token is missing, invalid, or does not identify a customer.")
    })
    public ResponseEntity<List<ServiceCatalogueResponse>> getCatalogue(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicesCatalogueService.getCatalogue(extractCustomerId(jwt)));
    }

    @GetMapping("/catalog/{serviceCode}")
    @Operation(
            summary = "Get a Mavela service catalogue entry",
            description = "Returns safe availability metadata only. Unknown and disabled codes are not exposed as purchasable services."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catalogue entry returned."),
            @ApiResponse(responseCode = "401", description = "The bearer token is missing, invalid, or does not identify a customer."),
            @ApiResponse(responseCode = "404", description = "The requested service is not available in the catalogue.")
    })
    public ResponseEntity<ServiceCatalogueResponse> getService(
            @PathVariable String serviceCode,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(servicesCatalogueService.getService(
                        extractCustomerId(jwt),
                        serviceCode
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
