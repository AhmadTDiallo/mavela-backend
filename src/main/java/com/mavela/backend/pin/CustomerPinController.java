package com.mavela.backend.pin;

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
public class CustomerPinController {

    private final CustomerPinService customerPinService;

    public CustomerPinController(
            CustomerPinService customerPinService
    ) {
        this.customerPinService = customerPinService;
    }

    @PostMapping
    public ResponseEntity<CustomerPinSetupResponse> setPin(
            @PathVariable UUID customerId,
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