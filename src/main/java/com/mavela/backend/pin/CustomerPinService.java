package com.mavela.backend.pin;

import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.customer.CustomerStatus;
import com.mavela.backend.error.ApiErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomerPinService {

    private static final Set<String> WEAK_PINS = Set.of(
            "0000",
            "1111",
            "2222",
            "3333",
            "4444",
            "5555",
            "6666",
            "7777",
            "8888",
            "9999",
            "0123",
            "1234",
            "2345",
            "3456",
            "4567",
            "5678",
            "6789",
            "9876",
            "8765",
            "7654",
            "6543",
            "5432",
            "4321",
            "3210"
    );

    private final CustomerRepository customerRepository;
    private final CustomerPinCredentialRepository credentialRepository;
    private final PinSetupTokenRepository tokenRepository;
    private final PinSetupTokenManager tokenManager;
    private final CustomerPinHasher pinHasher;

    public CustomerPinService(
            CustomerRepository customerRepository,
            CustomerPinCredentialRepository credentialRepository,
            PinSetupTokenRepository tokenRepository,
            PinSetupTokenManager tokenManager,
            CustomerPinHasher pinHasher
    ) {
        this.customerRepository = customerRepository;
        this.credentialRepository = credentialRepository;
        this.tokenRepository = tokenRepository;
        this.tokenManager = tokenManager;
        this.pinHasher = pinHasher;
    }

    @Transactional(
            noRollbackFor = CustomerPinSetupException.class
    )
    public CustomerPinSetupResponse setPin(
            UUID customerId,
            SetCustomerPinRequest request
    ) {
        Instant now = Instant.now();

        /*
         * The customer row lock serializes OTP verification, username
         * selection, and PIN setup for the same customer.
         */
        Customer customer = customerRepository
                .findByIdForUpdate(customerId)
                .orElseThrow(() ->
                        new CustomerPinSetupException(
                                ApiErrorCode.CUSTOMER_NOT_FOUND,
                                HttpStatus.NOT_FOUND
                        )
                );

        if (!customer.isPhoneVerified()) {
            throw new CustomerPinSetupException(
                    ApiErrorCode.PHONE_NOT_VERIFIED,
                    HttpStatus.CONFLICT
            );
        }

        if (customer.getStatus() == CustomerStatus.SUSPENDED
                || customer.getStatus() == CustomerStatus.CLOSED) {
            throw new CustomerPinSetupException(
                    ApiErrorCode.PIN_SETUP_NOT_ALLOWED,
                    HttpStatus.FORBIDDEN
            );
        }

        if (credentialRepository.existsById(customerId)) {
            throw new CustomerPinSetupException(
                    ApiErrorCode.PIN_ALREADY_SET,
                    HttpStatus.CONFLICT
            );
        }

        PinSetupToken setupToken = tokenRepository
                .findFirstByCustomer_IdAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
                        customerId
                )
                .orElseThrow(() ->
                        new CustomerPinSetupException(
                                ApiErrorCode.PIN_SETUP_TOKEN_INVALID,
                                HttpStatus.FORBIDDEN
                        )
                );

        if (!tokenManager.matches(
                request.pinSetupToken(),
                setupToken.getTokenHash()
        )) {
            throw new CustomerPinSetupException(
                    ApiErrorCode.PIN_SETUP_TOKEN_INVALID,
                    HttpStatus.FORBIDDEN
            );
        }

        if (setupToken.isExpired(now)) {
            setupToken.invalidate(now);

            throw new CustomerPinSetupException(
                    ApiErrorCode.PIN_SETUP_TOKEN_EXPIRED,
                    HttpStatus.GONE
            );
        }

        if (!customer.hasUsername()) {
            throw new CustomerPinSetupException(
                    ApiErrorCode.USERNAME_REQUIRED_BEFORE_PIN_SETUP,
                    HttpStatus.CONFLICT
            );
        }

        if (WEAK_PINS.contains(request.pin())) {
            throw new CustomerPinSetupException(
                    ApiErrorCode.PIN_TOO_WEAK,
                    HttpStatus.BAD_REQUEST
            );
        }

        String pinHash = pinHasher.hash(
                customerId,
                request.pin()
        );

        CustomerPinCredential credential =
                new CustomerPinCredential(
                        customer,
                        pinHash,
                        now
                );

        /*
         * The credential, token consumption, and activation all belong
         * to this transaction. A persistence failure rolls them all back.
         */
        credentialRepository.saveAndFlush(credential);

        setupToken.consume(now);
        customer.activateAfterPinSetup();

        return new CustomerPinSetupResponse(
                customer.getId(),
                customer.getStatus(),
                credential.getCreatedAt()
        );
    }
}