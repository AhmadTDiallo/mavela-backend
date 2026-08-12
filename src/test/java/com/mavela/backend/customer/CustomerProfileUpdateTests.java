package com.mavela.backend.customer;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerProfileUpdateTests {

    private static final Instant FIRST_COMPLETED_AT =
            Instant.parse("2026-08-11T12:00:00Z");

    @Test
    void partialProfileUpdateDoesNotMarkProfileComplete() {
        Customer customer = newCustomer();

        customer.updateProfile(
                null,
                null,
                null,
                LocalDate.of(1995, 4, 12),
                null,
                null,
                null,
                null,
                null,
                FIRST_COMPLETED_AT
        );

        assertNull(customer.getProfileCompletedAt());
    }

    @Test
    void completeProfileUpdateSetsProfileCompletedAt() {
        Customer customer = newCustomer();

        updateAllProfileFields(customer, FIRST_COMPLETED_AT);

        assertEquals(FIRST_COMPLETED_AT, customer.getProfileCompletedAt());
    }

    @Test
    void laterProfileUpdateDoesNotChangeOriginalProfileCompletedAt() {
        Customer customer = newCustomer();
        updateAllProfileFields(customer, FIRST_COMPLETED_AT);

        customer.updateProfile(
                "Grace",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                FIRST_COMPLETED_AT.plusSeconds(60)
        );

        assertEquals(FIRST_COMPLETED_AT, customer.getProfileCompletedAt());
        assertEquals("Grace", customer.getFirstName());
    }

    @Test
    void customerServiceNormalizesLowercaseNationality() {
        Customer customer = newCustomer();
        CustomerService service = new CustomerService(
                repositoryReturning(customer)
        );

        CustomerResponse response = service.updateCurrentCustomerProfile(
                UUID.randomUUID(),
                new UpdateCustomerProfileRequest(
                        null,
                        null,
                        null,
                        null,
                        "cd",
                        null,
                        null,
                        null,
                        null
                )
        );

        assertEquals("CD", response.nationality());
        assertEquals("CD", customer.getNationality());
    }

    @Test
    void futureDateOfBirthIsRejectedByRequestValidation() {
        Validator validator = Validation
                .buildDefaultValidatorFactory()
                .getValidator();
        UpdateCustomerProfileRequest request =
                new UpdateCustomerProfileRequest(
                        null,
                        null,
                        null,
                        LocalDate.now().plusDays(1),
                        null,
                        null,
                        null,
                        null,
                        null
                );

        assertFalse(validator.validate(request).isEmpty());
        assertTrue(
                validator.validate(request)
                        .stream()
                        .anyMatch(violation ->
                                violation.getPropertyPath()
                                        .toString()
                                        .equals("dateOfBirth")
                        )
        );
    }

    private Customer newCustomer() {
        return new Customer(
                "+243812345678",
                "ada@example.com",
                "Ada",
                "Lovelace",
                "en"
        );
    }

    private void updateAllProfileFields(
            Customer customer,
            Instant completedAt
    ) {
        customer.updateProfile(
                "Ada",
                "Lovelace",
                "en",
                LocalDate.of(1995, 4, 12),
                "CD",
                Gender.FEMALE,
                "1 Mavela Avenue",
                "Kinshasa",
                "Kinshasa",
                completedAt
        );
    }

    private CustomerRepository repositoryReturning(Customer customer) {
        return (CustomerRepository) Proxy.newProxyInstance(
                CustomerRepository.class.getClassLoader(),
                new Class<?>[]{CustomerRepository.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "findById" -> Optional.of(customer);
                    case "saveAndFlush" -> arguments[0];
                    default -> throw new UnsupportedOperationException(
                            method.getName()
                    );
                }
        );
    }
}
