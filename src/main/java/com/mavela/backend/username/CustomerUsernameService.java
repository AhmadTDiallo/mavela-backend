package com.mavela.backend.username;

import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.customer.CustomerStatus;
import com.mavela.backend.error.ApiErrorCode;
import com.mavela.backend.pin.CustomerPinCredentialRepository;
import com.mavela.backend.pin.PinSetupToken;
import com.mavela.backend.pin.PinSetupTokenManager;
import com.mavela.backend.pin.PinSetupTokenRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CustomerUsernameService {

    private final CustomerRepository customerRepository;
    private final CustomerPinCredentialRepository credentialRepository;
    private final PinSetupTokenRepository tokenRepository;
    private final PinSetupTokenManager tokenManager;

    public CustomerUsernameService(
            CustomerRepository customerRepository,
            CustomerPinCredentialRepository credentialRepository,
            PinSetupTokenRepository tokenRepository,
            PinSetupTokenManager tokenManager
    ) {
        this.customerRepository = customerRepository;
        this.credentialRepository = credentialRepository;
        this.tokenRepository = tokenRepository;
        this.tokenManager = tokenManager;
    }

    @Transactional(
            noRollbackFor =
                    CustomerUsernameTokenExpiredException.class
    )
    public CustomerUsernameResponse selectUsername(
            UUID customerId,
            SetCustomerUsernameRequest request
    ) {
        Instant now = Instant.now();

        Customer customer = customerRepository
                .findByIdForUpdate(customerId)
                .orElseThrow(() ->
                        new CustomerUsernameException(
                                ApiErrorCode.CUSTOMER_NOT_FOUND,
                                HttpStatus.NOT_FOUND
                        )
                );

        if (!customer.isPhoneVerified()) {
            throw new CustomerUsernameException(
                    ApiErrorCode.PHONE_NOT_VERIFIED,
                    HttpStatus.CONFLICT
            );
        }

        if (customer.getStatus() != CustomerStatus.PENDING
                || credentialRepository.existsById(customerId)) {
            throw new CustomerUsernameException(
                    ApiErrorCode.USERNAME_SETUP_NOT_ALLOWED,
                    HttpStatus.CONFLICT
            );
        }

        PinSetupToken setupToken = tokenRepository
                .findFirstByCustomer_IdAndConsumedAtIsNullAndInvalidatedAtIsNullOrderByCreatedAtDesc(
                        customerId
                )
                .orElseThrow(() ->
                        new CustomerUsernameException(
                                ApiErrorCode.PIN_SETUP_TOKEN_INVALID,
                                HttpStatus.FORBIDDEN
                        )
                );

        if (!tokenManager.matches(
                request.pinSetupToken(),
                setupToken.getTokenHash()
        )) {
            throw new CustomerUsernameException(
                    ApiErrorCode.PIN_SETUP_TOKEN_INVALID,
                    HttpStatus.FORBIDDEN
            );
        }

        if (setupToken.isExpired(now)) {
            setupToken.invalidate(now);

            throw new CustomerUsernameTokenExpiredException();
        }

        String username = request.username();

        /*
         * Repeating the same PUT request is safely idempotent.
         */
        if (username.equals(customer.getUsername())) {
            return CustomerUsernameResponse.from(customer);
        }

        if (customerRepository
                .existsByUsernameIgnoreCase(username)) {
            throw new CustomerUsernameException(
                    ApiErrorCode.USERNAME_ALREADY_TAKEN,
                    HttpStatus.CONFLICT
            );
        }

        customer.selectUsername(username);

        try {
            Customer savedCustomer =
                    customerRepository.saveAndFlush(customer);

            return CustomerUsernameResponse.from(savedCustomer);
        } catch (DataIntegrityViolationException exception) {
            /*
             * The unique database index protects against two customers
             * selecting the same username simultaneously.
             */
            throw new CustomerUsernameException(
                    ApiErrorCode.USERNAME_ALREADY_TAKEN,
                    HttpStatus.CONFLICT
            );
        }
    }
}