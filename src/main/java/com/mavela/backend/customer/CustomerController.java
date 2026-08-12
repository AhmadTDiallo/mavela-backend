package com.mavela.backend.customer;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(
        name = "Customers",
        description = "Customer registration and authenticated profile access."
)
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @Operation(
            summary = "Register a customer",
            description = "Creates a customer in the onboarding flow."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Customer registered successfully."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The registration request is invalid."
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "A customer already uses the supplied phone number or email address."
            )
    })
    public ResponseEntity<CustomerResponse> register(
            @Valid @RequestBody RegisterCustomerRequest request
    ) {
        CustomerResponse response =
                customerService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/me")
    @Operation(
            summary = "Get the current customer",
            description = "Returns the customer identified by the bearer token."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Current customer returned successfully."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "The bearer token is missing, invalid, or does not identify a customer."
            )
    })
    public ResponseEntity<CustomerResponse> getCurrentCustomer(
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        UUID customerId = extractCustomerId(jwt);

        CustomerResponse response =
                customerService.getCurrentCustomer(customerId);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    @Operation(
            summary = "Update the current customer profile",
            description = "Partially updates the profile of the customer identified by the bearer token."
    )
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Customer profile updated successfully."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The profile update request is invalid."
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "The bearer token is missing, invalid, or does not identify a customer."
            )
    })
    public ResponseEntity<CustomerResponse> updateCurrentCustomerProfile(
            @Valid @RequestBody UpdateCustomerProfileRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt
    ) {
        UUID customerId = extractCustomerId(jwt);

        CustomerResponse response =
                customerService.updateCurrentCustomerProfile(
                        customerId,
                        request
                );

        return ResponseEntity.ok(response);
    }

    private UUID extractCustomerId(Jwt jwt) {
        String subject = jwt.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_ACCESS_TOKEN_SUBJECT"
            );
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_ACCESS_TOKEN_SUBJECT"
            );
        }
    }
}
