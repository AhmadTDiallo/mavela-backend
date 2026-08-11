package com.mavela.backend.pin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/pin")
@Tag(
        name = "PIN",
        description = "PIN creation during customer onboarding."
)
public class CustomerPinController {

    private final CustomerPinService customerPinService;

    public CustomerPinController(
            CustomerPinService customerPinService
    ) {
        this.customerPinService = customerPinService;
    }

    @PostMapping
    @Operation(
            summary = "Set a customer PIN",
            description = "Creates the customer's four-digit PIN after phone verification and username selection."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "PIN created and customer activated."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The PIN setup request is invalid or the PIN is too weak."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "The PIN setup token is invalid or PIN setup is not permitted."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The customer does not exist."
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Phone verification, username selection, or PIN creation is not in a valid state."
            ),
            @ApiResponse(
                    responseCode = "410",
                    description = "The PIN setup token has expired."
            )
    })
    public ResponseEntity<CustomerPinSetupResponse> setPin(
            @Parameter(
                    description = "Identifier of the customer setting a PIN."
            ) @PathVariable UUID customerId,
            @Valid @RequestBody SetCustomerPinRequest request
    ) {
        CustomerPinSetupResponse response =
                customerPinService.setPin(
                        customerId,
                        request
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
