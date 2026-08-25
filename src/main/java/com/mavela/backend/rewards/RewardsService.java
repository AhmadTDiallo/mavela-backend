package com.mavela.backend.rewards;

import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.error.ApiErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative non-monetary Mavela Gems and daily-streak workflow.
 * Every lookup is scoped to the authenticated customer UUID.
 */
@Service
public class RewardsService {

    static final ZoneId BUSINESS_ZONE = ZoneId.of("Africa/Kinshasa");
    private static final int DAILY_CHECK_IN_GEMS = 5;
    private static final int RECENT_ACTIVITY_SIZE = 5;
    private static final int MAX_ACTIVITY_PAGE_SIZE = 50;
    private static final List<GemsMilestoneResponse> MILESTONES = List.of(
            new GemsMilestoneResponse(7, 50),
            new GemsMilestoneResponse(14, 100),
            new GemsMilestoneResponse(30, 300)
    );

    private final CustomerRepository customerRepository;
    private final GemsLedgerEntryRepository ledgerRepository;
    private final CustomerRewardStreakRepository streakRepository;
    private final Clock clock;

    public RewardsService(
            CustomerRepository customerRepository,
            GemsLedgerEntryRepository ledgerRepository,
            CustomerRewardStreakRepository streakRepository,
            @Qualifier("rewardsClock") Clock clock
    ) {
        this.customerRepository = customerRepository;
        this.ledgerRepository = ledgerRepository;
        this.streakRepository = streakRepository;
        this.clock = clock;
    }

    /**
     * Processes a daily check-in atomically. The customer row lock serializes
     * the customer command and the partial daily-check-in unique index is the
     * final PostgreSQL guard against a duplicate award.
     */
    @Transactional
    public DailyCheckInResponse claimDailyCheckIn(UUID authenticatedCustomerId) {
        Customer customer = customerRepository
                .findByIdForUpdate(authenticatedCustomerId)
                .orElseThrow(this::authenticatedCustomerNotFound);
        LocalDate businessDate = currentBusinessDate();

        /*
         * A native upsert ensures exactly one streak state row exists even if
         * a future server command creates one before this customer checks in.
         */
        streakRepository.createIfAbsent(authenticatedCustomerId, clock.instant());
        CustomerRewardStreak streak = streakRepository
                .findByCustomerIdForUpdate(authenticatedCustomerId)
                .orElseThrow(this::authenticatedCustomerNotFound);

        if (ledgerRepository.findByCustomer_IdAndEntryTypeAndBusinessDate(
                authenticatedCustomerId,
                GemsLedgerEntryType.DAILY_CHECK_IN,
                businessDate
        ).isPresent()) {
            return alreadyCompletedResponse(authenticatedCustomerId, streak);
        }

        int currentStreak = streak.recordQualifiedCheckIn(businessDate);
        GemsMilestoneResponse milestone = milestoneAt(currentStreak).orElse(null);
        int awardedGems = DAILY_CHECK_IN_GEMS;

        ledgerRepository.save(GemsLedgerEntry.dailyCheckIn(
                customer,
                businessDate,
                DAILY_CHECK_IN_GEMS
        ));
        if (milestone != null) {
            ledgerRepository.save(GemsLedgerEntry.streakMilestone(
                    customer,
                    businessDate,
                    milestone.day(),
                    milestone.gems()
            ));
            awardedGems += milestone.gems();
        }

        /* Flush the streak state and both ledger entries before deriving the balance. */
        ledgerRepository.flush();

        return new DailyCheckInResponse(
                availableBalance(authenticatedCustomerId),
                awardedGems,
                currentStreak,
                false,
                milestone,
                nextMilestone(currentStreak).orElse(null),
                milestone == null
                        ? "Daily check-in complete."
                        : "Daily check-in complete. You reached a streak milestone."
        );
    }

    @Transactional(readOnly = true)
    public GemsSummaryResponse getSummary(UUID authenticatedCustomerId) {
        requireAuthenticatedCustomer(authenticatedCustomerId);
        LocalDate businessDate = currentBusinessDate();
        int currentStreak = streakRepository.findById(authenticatedCustomerId)
                .map(CustomerRewardStreak::getCurrentStreakDays)
                .orElse(0);
        boolean checkedInToday = ledgerRepository
                .findByCustomer_IdAndEntryTypeAndBusinessDate(
                        authenticatedCustomerId,
                        GemsLedgerEntryType.DAILY_CHECK_IN,
                        businessDate
                )
                .isPresent();
        List<GemsActivityEntryResponse> recentActivity = ledgerRepository
                .findByCustomer_IdOrderByCreatedAtDesc(
                        authenticatedCustomerId,
                        PageRequest.of(0, RECENT_ACTIVITY_SIZE)
                )
                .getContent()
                .stream()
                .map(GemsActivityEntryResponse::from)
                .toList();

        return new GemsSummaryResponse(
                availableBalance(authenticatedCustomerId),
                currentStreak,
                checkedInToday,
                nextMilestone(currentStreak).orElse(null),
                recentActivity
        );
    }

    @Transactional(readOnly = true)
    public GemsActivityPageResponse getActivity(
            UUID authenticatedCustomerId,
            int page,
            int requestedSize
    ) {
        requireAuthenticatedCustomer(authenticatedCustomerId);
        if (page < 0 || requestedSize < 1) {
            throw new RewardsException(
                    ApiErrorCode.REWARDS_INVALID_PAGINATION,
                    HttpStatus.BAD_REQUEST
            );
        }

        Page<GemsLedgerEntry> results = ledgerRepository
                .findByCustomer_IdOrderByCreatedAtDesc(
                        authenticatedCustomerId,
                        PageRequest.of(
                                page,
                                Math.min(requestedSize, MAX_ACTIVITY_PAGE_SIZE)
                        )
                );

        return new GemsActivityPageResponse(
                results.getContent().stream()
                        .map(GemsActivityEntryResponse::from)
                        .toList(),
                results.getNumber(),
                results.getSize(),
                results.getTotalElements(),
                results.getTotalPages()
        );
    }

    private DailyCheckInResponse alreadyCompletedResponse(
            UUID customerId,
            CustomerRewardStreak streak
    ) {
        int currentStreak = streak.getCurrentStreakDays();
        return new DailyCheckInResponse(
                availableBalance(customerId),
                0,
                currentStreak,
                true,
                null,
                nextMilestone(currentStreak).orElse(null),
                "Today's daily check-in is already complete."
        );
    }

    private Customer requireAuthenticatedCustomer(UUID customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(this::authenticatedCustomerNotFound);
    }

    private long availableBalance(UUID customerId) {
        return ledgerRepository.availableBalanceForCustomer(
                customerId,
                GemsLedgerEntryStatus.AVAILABLE
        );
    }

    private LocalDate currentBusinessDate() {
        return businessDate(clock);
    }

    public static LocalDate businessDate(Clock clock) {
        return clock.instant().atZone(BUSINESS_ZONE).toLocalDate();
    }

    private Optional<GemsMilestoneResponse> milestoneAt(int currentStreak) {
        return MILESTONES.stream()
                .filter(milestone -> milestone.day() == currentStreak)
                .findFirst();
    }

    private Optional<GemsMilestoneResponse> nextMilestone(int currentStreak) {
        return MILESTONES.stream()
                .filter(milestone -> milestone.day() > currentStreak)
                .findFirst();
    }

    private RewardsException authenticatedCustomerNotFound() {
        return new RewardsException(
                ApiErrorCode.REWARDS_CUSTOMER_NOT_FOUND,
                HttpStatus.UNAUTHORIZED
        );
    }
}
