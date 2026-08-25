package com.mavela.backend.admin.rewards;

import com.mavela.backend.TestcontainersConfiguration;
import com.mavela.backend.admin.auth.AdminPermission;
import com.mavela.backend.admin.staff.StaffUser;
import com.mavela.backend.admin.staff.StaffUserRepository;
import com.mavela.backend.admin.staff.StaffUserStatus;
import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.error.ApiErrorCode;
import com.mavela.backend.rewards.GemsLedgerEntry;
import com.mavela.backend.rewards.GemsLedgerEntryRepository;
import com.mavela.backend.rewards.GemsLedgerEntryStatus;
import com.mavela.backend.rewards.GemsLedgerEntryType;
import com.mavela.backend.rewards.RewardsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RewardsAdminServiceIntegrationTests {

    @Autowired
    private RewardsAdminService rewardsAdminService;

    @Autowired
    private RewardsService rewardsService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private GemsLedgerEntryRepository ledgerRepository;

    @Autowired
    private RewardsAdminAuditEventRepository auditRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanDatabase() {
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM rewards_admin_audit_events");
        jdbcTemplate.update("DELETE FROM gems_ledger_entries");
        jdbcTemplate.update("DELETE FROM customer_reward_streaks");
        customerRepository.deleteAll();
        staffUserRepository.deleteAll();
    }

    @Test
    void v16AddsAuditAndPublicIdentifierSchema() {
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '16' AND success = TRUE
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'rewards_admin_audit_events'
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname = 'uq_gems_ledger_entries_one_reversal_per_original'
                """, Integer.class));
    }

    @Test
    void populatedV15SchemaUpgradesWithoutChangingExistingLedgerRows() {
        String schema = "rewards_v16_upgrade_" + UUID.randomUUID()
                .toString().replace("-", "");
        UUID customerId = UUID.randomUUID();
        UUID ledgerId = UUID.randomUUID();
        try {
            migrateSchema(schema, "15");
            jdbcTemplate.update("""
                    INSERT INTO %s.customers (
                        id, username, phone_number, first_name, last_name,
                        preferred_locale, status, kyc_status, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted(schema),
                    customerId, "legacyrewards", "+243810000001", "Legacy",
                    "Rewards", "en", "PENDING", "NOT_STARTED");
            jdbcTemplate.update("""
                    INSERT INTO %s.gems_ledger_entries (
                        id, customer_id, entry_type, amount, status,
                        business_date, reference, actor_type, created_at
                    ) VALUES (?, ?, ?, ?, ?, CURRENT_DATE, ?, ?, CURRENT_TIMESTAMP)
                    """.formatted(schema),
                    ledgerId, customerId, "DAILY_CHECK_IN", 5, "AVAILABLE",
                    "Daily check-in", "CUSTOMER");

            migrateSchema(schema, "16");

            assertEquals(5, jdbcTemplate.queryForObject("""
                    SELECT amount FROM %s.gems_ledger_entries WHERE id = ?
                    """.formatted(schema), Integer.class, ledgerId));
            assertEquals(1, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM %s.customers WHERE public_id IS NOT NULL
                    """.formatted(schema), Integer.class));
            assertEquals(1, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM %s.gems_ledger_entries WHERE public_id IS NOT NULL
                    """.formatted(schema), Integer.class));
            assertEquals(1, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = ? AND table_name = 'rewards_admin_audit_events'
                    """, Integer.class, schema));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void rewardsManagerCanSearchButKycReviewerCannotAccessRewards() {
        Customer customer = customer("Ada", "Rewards");
        StaffUser manager = staff("rewards-manager", StaffUserStatus.ACTIVE);
        authenticate(manager, AdminPermission.REWARDS_READ);

        AdminRewardsCustomerPageResponse page = rewardsAdminService
                .searchCustomers(manager.getExternalSubject(), "Ada", 0, 25);

        assertEquals(1, page.totalElements());
        AdminRewardsCustomerResponse result = page.content().getFirst();
        assertEquals("$" + customer.getUsername(), result.handle());
        assertNotEquals(customer.getId(), result.customerId());
        assertFalse(result.maskedPhoneNumber().contains(
                customer.getPhoneNumber().substring(1, 5)
        ));

        authenticate(manager, AdminPermission.KYC_READ);
        assertThrows(AccessDeniedException.class, () -> rewardsAdminService
                .searchCustomers(manager.getExternalSubject(), "Ada", 0, 25));
    }

    @Test
    void inactiveStaffAndInvalidLookupFailSafely() {
        Customer customer = customer("Ari", "Inactive");
        StaffUser inactive = staff("disabled-rewards", StaffUserStatus.DISABLED);
        authenticate(inactive, AdminPermission.REWARDS_READ);

        assertThrows(RuntimeException.class, () -> rewardsAdminService.getSummary(
                inactive.getExternalSubject(), customer.getPublicId()
        ));

        StaffUser manager = staff("active-rewards", StaffUserStatus.ACTIVE);
        authenticate(manager, AdminPermission.REWARDS_READ);
        RewardsAdminException error = assertThrows(
                RewardsAdminException.class,
                () -> rewardsAdminService.searchCustomers(
                        manager.getExternalSubject(), " ", 0, 25
                )
        );
        assertEquals(ApiErrorCode.REWARDS_ADMIN_INVALID_SEARCH_QUERY,
                error.getCode());
    }

    @Test
    void summaryAndActivityAreCustomerScopedAndCapped() {
        Customer owner = customer("Owner", "One");
        Customer other = customer("Other", "Two");
        ledgerRepository.saveAndFlush(entry(owner, 8, "owner-entry"));
        ledgerRepository.saveAndFlush(entry(other, 91, "other-entry"));
        StaffUser manager = staff("reader", StaffUserStatus.ACTIVE);
        authenticate(manager, AdminPermission.REWARDS_READ);

        AdminRewardsCustomerSummaryResponse summary = rewardsAdminService
                .getSummary(manager.getExternalSubject(), owner.getPublicId());
        AdminRewardsActivityPageResponse activity = rewardsAdminService
                .getActivity(manager.getExternalSubject(), owner.getPublicId(), 0, 999);

        assertEquals(8, summary.availableGems());
        assertEquals(1, activity.content().size());
        assertEquals(50, activity.size());
        assertEquals(8, activity.content().getFirst().amount());
        assertFalse(activity.content().getFirst().reference().contains("other"));
    }

    @Test
    void manualAdjustmentChangesOnlyLedgerAndKeepsStaffNotesOutOfCustomerApi() {
        Customer customer = customer("Manual", "Adjustment");
        StaffUser manager = staff("adjuster", StaffUserStatus.ACTIVE);
        authenticate(manager, AdminPermission.REWARDS_ADJUST);

        AdminRewardsCustomerSummaryResponse summary = rewardsAdminService
                .createAdjustment(manager.getExternalSubject(), customer.getPublicId(),
                        adjustment(40, "adjustment-note-is-staff-only", "adjust-001"));

        assertEquals(40, summary.availableGems());
        assertEquals(1, ledgerRepository.count());
        assertEquals(GemsLedgerEntryType.MANUAL_ADJUSTMENT,
                ledgerRepository.findAll().getFirst().getEntryType());
        assertEquals(1, auditRepository.count());
        assertTrue(rewardsService.getActivity(customer.getId(), 0, 25).content()
                .stream().noneMatch(entry -> entry.reference()
                        .contains("staff-only")));

        authenticate(manager, AdminPermission.REWARDS_READ);
        AdminRewardsAuditPageResponse audit = rewardsAdminService
                .getAuditHistory(manager.getExternalSubject(), customer.getPublicId(),
                        0, 25);
        assertEquals(1, audit.content().size());
        assertEquals(40, audit.content().getFirst().amount());
        assertEquals(RewardsAdminAction.MANUAL_ADJUSTMENT,
                audit.content().getFirst().action());
    }

    @Test
    void idempotencyAndConfiguredLimitsPreventDuplicateOrExcessiveAdjustments() {
        Customer customer = customer("Idempotent", "Customer");
        StaffUser manager = staff("idempotent-manager", StaffUserStatus.ACTIVE);
        authenticate(manager, AdminPermission.REWARDS_ADJUST);
        CreateRewardsAdjustmentRequest request = adjustment(7, "Customer correction", "repeat-001");

        rewardsAdminService.createAdjustment(
                manager.getExternalSubject(), customer.getPublicId(), request
        );
        RewardsAdminException duplicate = assertThrows(
                RewardsAdminException.class,
                () -> rewardsAdminService.createAdjustment(
                        manager.getExternalSubject(), customer.getPublicId(), request
                )
        );
        assertEquals(ApiErrorCode.REWARDS_ADMIN_DUPLICATE_IDEMPOTENCY_KEY,
                duplicate.getCode());
        assertEquals(1, ledgerRepository.count());

        RewardsAdminException limit = assertThrows(
                RewardsAdminException.class,
                () -> rewardsAdminService.createAdjustment(
                        manager.getExternalSubject(), customer.getPublicId(),
                        adjustment(10_001, "Too large", "limit-001")
                )
        );
        assertEquals(ApiErrorCode.REWARDS_ADMIN_ADJUSTMENT_LIMIT_EXCEEDED,
                limit.getCode());
    }

    @Test
    void reversalIsOneCompensatingEntryAndCannotBeRepeated() {
        Customer customer = customer("Reverse", "Entry");
        StaffUser manager = staff("reversal-manager", StaffUserStatus.ACTIVE);
        authenticate(manager, AdminPermission.REWARDS_ADJUST);
        AdminRewardsCustomerSummaryResponse adjusted = rewardsAdminService
                .createAdjustment(manager.getExternalSubject(), customer.getPublicId(),
                        adjustment(15, "Correction", "reverse-adjust-001"));
        UUID activityId = adjusted.recentActivity().getFirst().activityId();

        AdminRewardsCustomerSummaryResponse reversed = rewardsAdminService
                .reverseActivity(manager.getExternalSubject(), customer.getPublicId(),
                        activityId, reversal("Duplicate award", "reverse-001"));

        assertEquals(0, reversed.availableGems());
        assertEquals(2, ledgerRepository.count());
        assertEquals(2, auditRepository.count());
        assertTrue(ledgerRepository.findAll().stream().anyMatch(entry ->
                entry.getEntryType() == GemsLedgerEntryType.REVERSAL
                        && entry.getAmount() == -15));

        RewardsAdminException duplicate = assertThrows(
                RewardsAdminException.class,
                () -> rewardsAdminService.reverseActivity(
                        manager.getExternalSubject(), customer.getPublicId(), activityId,
                        reversal("Duplicate award", "reverse-002")
                )
        );
        assertEquals(ApiErrorCode.REWARDS_ADMIN_REVERSAL_ALREADY_EXISTS,
                duplicate.getCode());
    }

    @Test
    void safeAuditHistoryContainsOperationsButNeverTheInternalNote() {
        Customer customer = customer("Audit", "History");
        StaffUser manager = staff("audit-manager", StaffUserStatus.ACTIVE);
        authenticate(manager, AdminPermission.REWARDS_ADJUST);
        rewardsAdminService.createAdjustment(
                manager.getExternalSubject(),
                customer.getPublicId(),
                adjustment(11, "private operational note", "audit-001")
        );

        authenticate(manager, AdminPermission.REWARDS_READ);
        AdminRewardsAuditPageResponse history = rewardsAdminService
                .getAuditHistory(
                        manager.getExternalSubject(), customer.getPublicId(), 0, 25
                );

        assertEquals(1, history.totalElements());
        assertEquals(RewardsAdminAction.MANUAL_ADJUSTMENT,
                history.content().getFirst().action());
        assertEquals(11, history.content().getFirst().amount());
        assertFalse(history.content().getFirst().toString()
                .contains("private operational note"));
    }

    @Test
    void concurrentRetryCannotDoubleCredit() throws Exception {
        Customer customer = customer("Concurrent", "Retry");
        StaffUser manager = staff("concurrent-manager", StaffUserStatus.ACTIVE);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentAdjustment(
                    manager, customer.getPublicId(), ready, start
            ));
            var second = executor.submit(() -> concurrentAdjustment(
                    manager, customer.getPublicId(), ready, start
            ));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS));
            assertTrue(second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, ledgerRepository.count());
        assertEquals(1, auditRepository.count());
    }

    private boolean concurrentAdjustment(
            StaffUser manager,
            UUID customerPublicId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        authenticate(manager, AdminPermission.REWARDS_ADJUST);
        ready.countDown();
        try {
            assertTrue(start.await(5, TimeUnit.SECONDS));
            try {
                rewardsAdminService.createAdjustment(
                        manager.getExternalSubject(), customerPublicId,
                        adjustment(9, "Concurrent retry", "concurrent-001")
                );
            } catch (RewardsAdminException exception) {
                assertEquals(ApiErrorCode.REWARDS_ADMIN_DUPLICATE_IDEMPOTENCY_KEY,
                        exception.getCode());
            }
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Customer customer(String firstName, String lastName) {
        String suffix = String.format("%08d",
                Math.abs(UUID.randomUUID().hashCode()) % 100_000_000L);
        Customer customer = new Customer(
                "+2438" + suffix,
                null,
                firstName,
                lastName,
                "en"
        );
        customer.selectUsername((firstName + suffix.substring(0, 3)).toLowerCase());
        return customerRepository.saveAndFlush(customer);
    }

    private StaffUser staff(String subject, StaffUserStatus status) {
        return staffUserRepository.saveAndFlush(new StaffUser(
                subject, subject + "@mavela.test", subject, status
        ));
    }

    private GemsLedgerEntry entry(Customer customer, int amount, String key) {
        return new GemsLedgerEntry(
                customer,
                GemsLedgerEntryType.MANUAL_ADJUSTMENT,
                amount,
                GemsLedgerEntryStatus.AVAILABLE,
                LocalDate.now(java.time.ZoneId.of("Africa/Kinshasa")),
                "Safe activity",
                key
        );
    }

    private CreateRewardsAdjustmentRequest adjustment(
            int amount, String note, String key
    ) {
        return new CreateRewardsAdjustmentRequest(
                amount, RewardsAdjustmentReasonCode.CUSTOMER_SERVICE_CORRECTION,
                note, key
        );
    }

    private CreateRewardsReversalRequest reversal(String note, String key) {
        return new CreateRewardsReversalRequest(
                RewardsReversalReasonCode.DUPLICATE_REWARD, note, key
        );
    }

    private void authenticate(StaffUser staff, AdminPermission... permissions) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                staff.getExternalSubject(), "test",
                List.of(permissions).stream()
                        .map(permission -> new SimpleGrantedAuthority(
                                permission.authority()))
                        .toList()
        );
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void migrateSchema(String schema, String targetVersion) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(targetVersion)
                .load()
                .migrate();
    }
}
