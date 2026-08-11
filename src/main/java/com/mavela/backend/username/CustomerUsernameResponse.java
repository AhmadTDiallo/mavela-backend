package com.mavela.backend.username;

import com.mavela.backend.customer.Customer;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CustomerUsernameResponse(
        @Schema(description = "Identifier of the customer.")
        UUID customerId,

        @Schema(description = "Selected customer username.")
        String username,

        @Schema(
                description = "Customer-facing handle composed from the username with a dollar-sign prefix."
        )
        String handle
) {

    public static CustomerUsernameResponse from(
            Customer customer
    ) {
        return new CustomerUsernameResponse(
                customer.getId(),
                customer.getUsername(),
                "$" + customer.getUsername()
        );
    }
}
