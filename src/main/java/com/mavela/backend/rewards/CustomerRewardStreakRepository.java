package com.mavela.backend.rewards;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRewardStreakRepository
        extends JpaRepository<CustomerRewardStreak, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO customer_reward_streaks (
                customer_id,
                current_streak_days,
                version,
                created_at,
                updated_at
            ) VALUES (:customerId, 0, 0, :now, :now)
            ON CONFLICT (customer_id) DO NOTHING
            """, nativeQuery = true)
    int createIfAbsent(
            @Param("customerId") UUID customerId,
            @Param("now") java.time.Instant now
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT streak
            FROM CustomerRewardStreak streak
            WHERE streak.customerId = :customerId
            """)
    Optional<CustomerRewardStreak> findByCustomerIdForUpdate(
            @Param("customerId") UUID customerId
    );
}
