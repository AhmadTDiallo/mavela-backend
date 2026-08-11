package com.mavela.backend.username;

import com.mavela.backend.customer.Customer;

import java.util.UUID;

public record CustomerUsernameResponse(
        UUID customerId,
        String username,
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