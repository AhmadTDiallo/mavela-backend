package com.mavela.backend.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository
        extends JpaRepository<Customer, UUID> {

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT customer
            FROM Customer customer
            WHERE customer.id = :customerId
            """)
    Optional<Customer> findByIdForUpdate(
            @Param("customerId") UUID customerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT customer
            FROM Customer customer
            WHERE customer.phoneNumber = :phoneNumber
            """)
    Optional<Customer> findByPhoneNumberForUpdate(
            @Param("phoneNumber") String phoneNumber
    );
}