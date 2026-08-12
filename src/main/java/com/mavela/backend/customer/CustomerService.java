package com.mavela.backend.customer;

import com.mavela.backend.error.ApiErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(
            CustomerRepository customerRepository
    ) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse register(
            RegisterCustomerRequest request
    ) {
        String phoneNumber = request.phoneNumber().trim();
        String email = normalizeEmail(request.email());
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();
        String preferredLocale =
                request.preferredLocale().trim();

        if (customerRepository.existsByPhoneNumber(phoneNumber)) {
            throw new DuplicateCustomerException(
                    ApiErrorCode.CUSTOMER_PHONE_ALREADY_EXISTS
            );
        }

        if (email != null
                && customerRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateCustomerException(
                    ApiErrorCode.CUSTOMER_EMAIL_ALREADY_EXISTS
            );
        }

        Customer customer = new Customer(
                phoneNumber,
                email,
                firstName,
                lastName,
                preferredLocale
        );

        try {
            Customer savedCustomer =
                    customerRepository.saveAndFlush(customer);

            return CustomerResponse.from(savedCustomer);
        } catch (DataIntegrityViolationException exception) {
            /*
             * Database constraints protect against two simultaneous
             * requests passing the duplicate checks.
             */
            throw new DuplicateCustomerException(
                    ApiErrorCode.CUSTOMER_ALREADY_EXISTS
            );
        }
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCurrentCustomer(
            UUID customerId
    ) {
        Customer customer = customerRepository
                .findById(customerId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "AUTHENTICATED_CUSTOMER_NOT_FOUND"
                        )
                );

        return CustomerResponse.from(customer);
    }

    @Transactional
    public CustomerResponse updateCurrentCustomerProfile(
            UUID customerId,
            UpdateCustomerProfileRequest request
    ) {
        Customer customer = customerRepository
                .findById(customerId)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.UNAUTHORIZED,
                                "AUTHENTICATED_CUSTOMER_NOT_FOUND"
                        )
                );

        customer.updateProfile(
                trim(request.firstName()),
                trim(request.lastName()),
                trim(request.preferredLocale()),
                request.dateOfBirth(),
                normalizeNationality(request.nationality()),
                request.gender(),
                trim(request.addressLine()),
                trim(request.city()),
                trim(request.province()),
                Instant.now()
        );

        Customer savedCustomer = customerRepository.saveAndFlush(customer);
        return CustomerResponse.from(savedCustomer);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }

        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private String normalizeNationality(String nationality) {
        String trimmedNationality = trim(nationality);
        if (trimmedNationality == null) {
            return null;
        }

        return trimmedNationality.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
