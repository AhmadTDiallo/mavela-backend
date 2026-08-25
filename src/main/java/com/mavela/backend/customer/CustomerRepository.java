package com.mavela.backend.customer;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    Optional<Customer> findByPublicId(UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT customer
            FROM Customer customer
            WHERE customer.publicId = :publicId
            """)
    Optional<Customer> findByPublicIdForUpdate(
            @Param("publicId") UUID publicId
    );

    /**
     * Narrow, staff-authorized operational lookup only. The caller is
     * responsible for applying the rewards permission and minimum query
     * length before invoking this repository method.
     */
    @Query("""
            SELECT customer
            FROM Customer customer
            WHERE lower(coalesce(customer.username, '')) LIKE lower(concat('%', :query, '%'))
               OR lower(customer.firstName) LIKE lower(concat('%', :query, '%'))
               OR lower(customer.lastName) LIKE lower(concat('%', :query, '%'))
               OR lower(concat(customer.firstName, ' ', customer.lastName)) LIKE lower(concat('%', :query, '%'))
               OR customer.phoneNumber LIKE concat('%', :phoneQuery, '%')
            """)
    Page<Customer> searchForRewardsAdministration(
            @Param("query") String query,
            @Param("phoneQuery") String phoneQuery,
            Pageable pageable
    );
}
