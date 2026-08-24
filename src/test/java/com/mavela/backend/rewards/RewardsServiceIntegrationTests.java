package com.mavela.backend.rewards;

import com.mavela.backend.TestcontainersConfiguration;
import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class RewardsServiceIntegrationTests {

    @Autowired
    private RewardsService rewardsService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private GemsLedgerEntryRepository ledgerRepository;

    @Autowired
    private CustomerRewardStreakRepository streakRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @AfterEach
    void cleanDatabase() {
        ledgerRepository.deleteAll();
        streakRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void v15RewardsSchemaAppliesSuccessfully() {
        Integer migrations = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '15' AND success = TRUE
                """,
                Integer.class
        );

        assertEquals(1, migrations);
    }

    @Test
    void firstCheckInAwardsFiveGemsAndStartsStreakOne() {
        Customer customer = saveCustomer();

        DailyCheckInResponse response = rewardsService.claimDailyCheckIn(customer.getId());

        assertEquals(5, response.awardedGems());
        assertEquals(5, response.availableGems());
        assertEquals(1, response.currentStreak());
        assertFalse(response.alreadyCompletedToday());
        assertNull(response.milestoneReached());
        assertEquals(7, response.nextMilestone().day());
        assertEquals(1, ledgerRepository.count());
    }

    @Test
    void sameDayRetryReturnsPersistedStateWithoutAnotherLedgerEntryOrAward() {
        Customer customer = saveCustomer();
        DailyCheckInResponse first = rewardsService.claimDailyCheckIn(customer.getId());
        DailyCheckInResponse retry = rewardsService.claimDailyCheckIn(customer.getId());

        assertTrue(retry.alreadyCompletedToday());
        assertEquals(0, retry.awardedGems());
        assertEquals(first.availableGems(), retry.availableGems());
        assertEquals(1, ledgerRepository.count());
    }

    @Test
    void nextBusinessDayIncrementsTheStreak() {
        Customer customer = saveCustomer();
        seedStreak(customer.getId(), 1, today().minusDays(1));

        DailyCheckInResponse response = rewardsService.claimDailyCheckIn(customer.getId());

        assertEquals(2, response.currentStreak());
        assertEquals(5, response.awardedGems());
    }

    @Test
    void missedBusinessDayResetsTheStreak() {
        Customer customer = saveCustomer();
        seedStreak(customer.getId(), 6, today().minusDays(2));

        DailyCheckInResponse response = rewardsService.claimDailyCheckIn(customer.getId());

        assertEquals(1, response.currentStreak());
        assertEquals(5, response.awardedGems());
        assertNull(response.milestoneReached());
    }

    @Test
    void streakMilestonesAreAwardedExactlyOnce() {
        assertMilestoneAward(7, 50);
        assertMilestoneAward(14, 100);
        assertMilestoneAward(30, 300);
    }

    @Test
    void concurrentCheckInsCannotDoubleAward() throws Exception {
        Customer customer = saveCustomer();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> claimWhenStarted(customer.getId(), ready, start));
            var second = executor.submit(() -> claimWhenStarted(customer.getId(), ready, start));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();

            DailyCheckInResponse firstResponse = first.get(5, TimeUnit.SECONDS);
            DailyCheckInResponse secondResponse = second.get(5, TimeUnit.SECONDS);

            assertEquals(5, firstResponse.availableGems());
            assertEquals(5, secondResponse.availableGems());
            assertEquals(1, List.of(firstResponse, secondResponse).stream()
                    .filter(DailyCheckInResponse::alreadyCompletedToday)
                    .count());
        }

        assertEquals(1, ledgerRepository.count());
    }

    @Test
    void summaryAndActivityAreDerivedFromAvailableLedgerEntriesOnly() {
        Customer customer = saveCustomer();
        rewardsService.claimDailyCheckIn(customer.getId());
        ledgerRepository.saveAndFlush(new GemsLedgerEntry(
                customer,
                GemsLedgerEntryType.MANUAL_ADJUSTMENT,
                12,
                GemsLedgerEntryStatus.AVAILABLE,
                today(),
                "Promotional adjustment",
                "test-adjustment:" + customer.getId()
        ));

        GemsSummaryResponse summary = rewardsService.getSummary(customer.getId());
        GemsActivityPageResponse activity = rewardsService.getActivity(customer.getId(), 0, 999);

        assertEquals(17, summary.availableGems());
        assertEquals(1, summary.currentStreak());
        assertTrue(summary.checkedInToday());
        assertEquals(2, summary.recentActivity().size());
        assertEquals(50, activity.size());
        assertEquals(2, activity.totalElements());
        assertEquals(GemsLedgerEntryType.MANUAL_ADJUSTMENT, activity.content().getFirst().entryType());
    }

    @Test
    void customerEndpointsAreAuthenticatedAndCustomerIsolated() throws Exception {
        Customer owner = saveCustomer();
        Customer otherCustomer = saveCustomer();

        mockMvc.perform(post("/api/v1/rewards/daily-check-in")
                        .with(authenticatedAs(owner.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableGems", is(5)));

        mockMvc.perform(get("/api/v1/rewards/summary")
                        .with(authenticatedAs(otherCustomer.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableGems", is(0)))
                .andExpect(jsonPath("$.currentStreak", is(0)));

        mockMvc.perform(get("/api/v1/rewards/activity?size=9999")
                        .with(authenticatedAs(otherCustomer.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size", is(50)))
                .andExpect(jsonPath("$.totalElements", is(0)));

        mockMvc.perform(post("/api/v1/rewards/daily-check-in")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/rewards/activity?size=0")
                        .with(authenticatedAs(owner.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("REWARDS_INVALID_PAGINATION")));

        mockMvc.perform(get("/api/v1/rewards/summary")
                        .with(authenticatedAs(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("REWARDS_CUSTOMER_NOT_FOUND")));
    }

    @Test
    void KinshasaBusinessDateIsNotTheSameAsUtcNearMidnight() {
        LocalDate businessDate = RewardsService.businessDate(Clock.fixed(
                Instant.parse("2026-01-01T23:30:00Z"),
                ZoneOffset.UTC
        ));

        assertEquals(LocalDate.of(2026, 1, 2), businessDate);
    }

    private void assertMilestoneAward(int milestoneDay, int milestoneGems) {
        Customer customer = saveCustomer();
        seedStreak(customer.getId(), milestoneDay - 1, today().minusDays(1));

        DailyCheckInResponse first = rewardsService.claimDailyCheckIn(customer.getId());
        DailyCheckInResponse retry = rewardsService.claimDailyCheckIn(customer.getId());

        assertEquals(milestoneDay, first.currentStreak());
        assertEquals(5 + milestoneGems, first.awardedGems());
        assertEquals(milestoneDay, first.milestoneReached().day());
        assertEquals(milestoneGems, first.milestoneReached().gems());
        assertTrue(retry.alreadyCompletedToday());
        assertEquals(0, retry.awardedGems());
        assertEquals(2, ledgerRepository.findByCustomer_IdAndBusinessDateAndEntryTypeIn(
                customer.getId(),
                today(),
                List.of(
                        GemsLedgerEntryType.DAILY_CHECK_IN,
                        GemsLedgerEntryType.STREAK_MILESTONE
                )
        ).size());
    }

    private DailyCheckInResponse claimWhenStarted(
            UUID customerId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        assertTrue(start.await(2, TimeUnit.SECONDS));
        return rewardsService.claimDailyCheckIn(customerId);
    }

    private void seedStreak(UUID customerId, int days, LocalDate lastQualifiedDate) {
        jdbcTemplate.update(
                """
                INSERT INTO customer_reward_streaks (
                    customer_id,
                    current_streak_days,
                    last_qualified_business_date,
                    last_check_in_business_date,
                    version,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                customerId,
                days,
                lastQualifiedDate,
                lastQualifiedDate
        );
    }

    private Customer saveCustomer() {
        long suffix = Integer.toUnsignedLong(UUID.randomUUID().hashCode())
                % 100000000L;
        Customer customer = new Customer(
                "+2438" + String.format("%08d", suffix),
                null,
                "Rewards",
                "Customer",
                "en"
        );
        return customerRepository.saveAndFlush(customer);
    }

    private LocalDate today() {
        return LocalDate.now(RewardsService.BUSINESS_ZONE);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedAs(
            UUID customerId
    ) {
        return jwt().jwt(jwt -> jwt.subject(customerId.toString()));
    }
}
