package com.mavela.backend.username;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping(
        "/api/v1/customers/{customerId}/username"
)
@Tag(
        name = "Usernames",
        description = "Username selection during customer onboarding."
)
public class CustomerUsernameController {

    private final CustomerUsernameService usernameService;

    public CustomerUsernameController(
            CustomerUsernameService usernameService
    ) {
        this.usernameService = usernameService;
    }

    @PutMapping
    @Operation(
            summary = "Select a customer username",
            description = "Sets the customer's username using the short-lived token returned after phone verification."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Username selected successfully."
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "The username selection request is invalid."
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "The PIN setup token is invalid."
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "The customer does not exist."
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Username selection is not allowed or the username is already taken."
            ),
            @ApiResponse(
                    responseCode = "410",
                    description = "The PIN setup token has expired."
            )
    })
    public ResponseEntity<CustomerUsernameResponse> selectUsername(
            @Parameter(
                    description = "Identifier of the customer selecting a username."
            ) @PathVariable UUID customerId,
            @Valid @RequestBody SetCustomerUsernameRequest request
    ) {
        CustomerUsernameResponse response =
                usernameService.selectUsername(
                        customerId,
                        request
                );

        return ResponseEntity.ok(response);
    }
}
