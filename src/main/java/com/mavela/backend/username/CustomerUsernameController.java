package com.mavela.backend.username;

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
public class CustomerUsernameController {

    private final CustomerUsernameService usernameService;

    public CustomerUsernameController(
            CustomerUsernameService usernameService
    ) {
        this.usernameService = usernameService;
    }

    @PutMapping
    public ResponseEntity<CustomerUsernameResponse> selectUsername(
            @PathVariable UUID customerId,
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