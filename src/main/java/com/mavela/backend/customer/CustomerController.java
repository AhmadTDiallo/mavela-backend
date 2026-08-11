package com.mavela.backend.customer;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
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
    public ResponseEntity<CustomerResponse> getCurrentCustomer(
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID customerId = extractCustomerId(jwt);

        CustomerResponse response =
                customerService.getCurrentCustomer(customerId);

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