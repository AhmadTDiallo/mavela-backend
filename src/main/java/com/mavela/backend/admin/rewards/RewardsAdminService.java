package com.mavela.backend.admin.rewards;

import com.mavela.backend.admin.staff.StaffUser;
import com.mavela.backend.admin.staff.StaffUserService;
import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.error.ApiErrorCode;
import com.mavela.backend.rewards.CustomerRewardStreak;
import com.mavela.backend.rewards.CustomerRewardStreakRepository;
import com.mavela.backend.rewards.GemsLedgerEntry;
import com.mavela.backend.rewards.GemsLedgerEntryRepository;
import com.mavela.backend.rewards.GemsLedgerEntryStatus;
import com.mavela.backend.rewards.GemsLedgerEntryType;
import com.mavela.backend.rewards.RewardsService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * The only staff path that may add Gems ledger rows. Balance remains a sum of
 * immutable AVAILABLE entries; correction commands never update a balance or
 * original ledger record.
 */
@Service
public class RewardsAdminService {

    private static final int SUMMARY_ACTIVITY_SIZE = 5;

    private final StaffUserService staffUserService;
    private final CustomerRepository customerRepository;
    private final GemsLedgerEntryRepository ledgerRepository;
    private final CustomerRewardStreakRepository streakRepository;
    private final RewardsAdminAuditEventRepository auditRepository;
    private final RewardsAdminProperties properties;
    private final Clock clock;

    public RewardsAdminService(
            StaffUserService staffUserService,
            CustomerRepository customerRepository,
            GemsLedgerEntryRepository ledgerRepository,
            CustomerRewardStreakRepository streakRepository,
            RewardsAdminAuditEventRepository auditRepository,
            RewardsAdminProperties properties,
            @Qualifier("rewardsClock") Clock clock
    ) {
        this.staffUserService = staffUserService;
        this.customerRepository = customerRepository;
        this.ledgerRepository = ledgerRepository;
        this.streakRepository = streakRepository;
        this.auditRepository = auditRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('rewards:read')")
    public AdminRewardsCustomerPageResponse searchCustomers(
            String subject, String requestedQuery, int page, int requestedSize
    ) {
        staffUserService.requireActiveStaff(subject);
        String query = searchQuery(requestedQuery);
        Page<Customer> result = customerRepository.searchForRewardsAdministration(
                query, phoneSearchValue(query), pageRequest(page, requestedSize)
        );
        return new AdminRewardsCustomerPageResponse(
                result.getContent().stream().map(this::triage).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('rewards:read')")
    public AdminRewardsCustomerSummaryResponse getSummary(
            String subject, UUID customerPublicId
    ) {
        staffUserService.requireActiveStaff(subject);
        return summary(requireCustomer(customerPublicId));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('rewards:read')")
    public AdminRewardsActivityPageResponse getActivity(
            String subject, UUID customerPublicId, int page, int requestedSize
    ) {
        staffUserService.requireActiveStaff(subject);
        Customer customer = requireCustomer(customerPublicId);
        Page<GemsLedgerEntry> result = ledgerRepository
                .findByCustomer_IdOrderByCreatedAtDesc(
                        customer.getId(), pageRequest(page, requestedSize)
                );
        return activityPage(result);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('rewards:read')")
    public AdminRewardsAuditPageResponse getAuditHistory(
            String subject, UUID customerPublicId, int page, int requestedSize
    ) {
        staffUserService.requireActiveStaff(subject);
        Customer customer = requireCustomer(customerPublicId);
        Page<RewardsAdminAuditEvent> result = auditRepository
                .findByCustomer_IdOrderByCreatedAtDesc(
                        customer.getId(), pageRequest(page, requestedSize)
                );
        return new AdminRewardsAuditPageResponse(
                result.getContent().stream()
                        .map(AdminRewardsAuditEventResponse::from)
                        .toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    @PreAuthorize("hasAuthority('rewards:adjust')")
    public AdminRewardsCustomerSummaryResponse createAdjustment(
            String subject,
            UUID customerPublicId,
            CreateRewardsAdjustmentRequest request
    ) {
        StaffUser staff = staffUserService.requireActiveStaff(subject);
        Customer customer = requireCustomerForUpdate(customerPublicId);
        validateAdjustment(request);
        String key = request.idempotencyKey().trim();
        rejectDuplicateKey(staff, customer, RewardsAdminAction.MANUAL_ADJUSTMENT, key);

        try {
            GemsLedgerEntry entry = ledgerRepository.saveAndFlush(
                    GemsLedgerEntry.manualAdjustment(
                            customer, request.amount(), businessDate(),
                            ledgerKey(staff, "adjustment", key),
                            staff.getId().toString()
                    )
            );
            auditRepository.saveAndFlush(new RewardsAdminAuditEvent(
                    staff, customer, entry, null,
                    RewardsAdminAction.MANUAL_ADJUSTMENT,
                    request.reasonCode().name(), request.internalNote().trim(),
                    entry.getAmount(), key
            ));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateKey();
        }
        return summary(customer);
    }

    @Transactional
    @PreAuthorize("hasAuthority('rewards:adjust')")
    public AdminRewardsCustomerSummaryResponse reverseActivity(
            String subject,
            UUID customerPublicId,
            UUID activityPublicId,
            CreateRewardsReversalRequest request
    ) {
        StaffUser staff = staffUserService.requireActiveStaff(subject);
        Customer customer = requireCustomerForUpdate(customerPublicId);
        validateReversal(request);
        GemsLedgerEntry original = ledgerRepository
                .findByCustomer_IdAndPublicId(customer.getId(), activityPublicId)
                .orElseThrow(this::customerNotFound);

        if (!reversible(original)) {
            throw new RewardsAdminException(
                    ApiErrorCode.REWARDS_ADMIN_REVERSAL_NOT_ALLOWED,
                    HttpStatus.CONFLICT
            );
        }
        if (ledgerRepository.existsByReversalOfEntry_Id(original.getId())
                || auditRepository.existsByOriginalLedgerEntry_Id(original.getId())) {
            throw alreadyReversed();
        }

        String key = request.idempotencyKey().trim();
        rejectDuplicateKey(staff, customer, RewardsAdminAction.REVERSAL, key);
        try {
            GemsLedgerEntry reversal = ledgerRepository.saveAndFlush(
                    GemsLedgerEntry.reversal(
                            customer, original, businessDate(),
                            ledgerKey(staff, "reversal", key),
                            staff.getId().toString()
                    )
            );
            auditRepository.saveAndFlush(new RewardsAdminAuditEvent(
                    staff, customer, reversal, original, RewardsAdminAction.REVERSAL,
                    request.reasonCode().name(), request.internalNote().trim(),
                    reversal.getAmount(), key
            ));
        } catch (DataIntegrityViolationException exception) {
            throw alreadyReversed();
        }
        return summary(customer);
    }

    private AdminRewardsCustomerSummaryResponse summary(Customer customer) {
        CustomerRewardStreak streak = streakRepository.findById(customer.getId())
                .orElse(null);
        long balance = balance(customer.getId());
        int streakDays = streak == null ? 0 : streak.getCurrentStreakDays();
        List<AdminRewardsActivityResponse> activity = ledgerRepository
                .findByCustomer_IdOrderByCreatedAtDesc(
                        customer.getId(), PageRequest.of(0, SUMMARY_ACTIVITY_SIZE)
                ).getContent().stream().map(this::activity).toList();
        return new AdminRewardsCustomerSummaryResponse(
                AdminRewardsCustomerResponse.from(customer, balance, streakDays),
                balance, streakDays,
                streak == null ? null : streak.getLastQualifiedBusinessDate(),
                streak == null ? null : streak.getLastCheckInBusinessDate(),
                activity, true
        );
    }

    private AdminRewardsCustomerResponse triage(Customer customer) {
        int streak = streakRepository.findById(customer.getId())
                .map(CustomerRewardStreak::getCurrentStreakDays).orElse(0);
        return AdminRewardsCustomerResponse.from(
                customer, balance(customer.getId()), streak
        );
    }

    private AdminRewardsActivityPageResponse activityPage(Page<GemsLedgerEntry> page) {
        return new AdminRewardsActivityPageResponse(
                page.getContent().stream().map(this::activity).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages()
        );
    }

    private AdminRewardsActivityResponse activity(GemsLedgerEntry entry) {
        boolean availableForReversal = reversible(entry)
                && !ledgerRepository.existsByReversalOfEntry_Id(entry.getId());
        return AdminRewardsActivityResponse.from(entry, availableForReversal);
    }

    private Customer requireCustomer(UUID publicId) {
        if (publicId == null) {
            throw customerNotFound();
        }
        return customerRepository.findByPublicId(publicId)
                .orElseThrow(this::customerNotFound);
    }

    private Customer requireCustomerForUpdate(UUID publicId) {
        if (publicId == null) {
            throw customerNotFound();
        }
        return customerRepository.findByPublicIdForUpdate(publicId)
                .orElseThrow(this::customerNotFound);
    }

    private void validateAdjustment(CreateRewardsAdjustmentRequest request) {
        if (request == null || request.amount() == null || request.amount() == 0
                || request.reasonCode() == null || !validNote(request.internalNote())
                || !validKey(request.idempotencyKey())) {
            throw invalidAdjustment();
        }
        if (Math.abs((long) request.amount())
                > properties.safeMaxAdjustmentAbsoluteAmount()) {
            throw new RewardsAdminException(
                    ApiErrorCode.REWARDS_ADMIN_ADJUSTMENT_LIMIT_EXCEEDED,
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
    }

    private void validateReversal(CreateRewardsReversalRequest request) {
        if (request == null || request.reasonCode() == null
                || !validNote(request.internalNote())
                || !validKey(request.idempotencyKey())) {
            throw new RewardsAdminException(
                    ApiErrorCode.REWARDS_ADMIN_REVERSAL_NOT_ALLOWED,
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
    }

    private void rejectDuplicateKey(
            StaffUser staff, Customer customer, RewardsAdminAction action, String key
    ) {
        if (auditRepository.existsByStaffUser_IdAndCustomer_IdAndActionAndIdempotencyKey(
                staff.getId(), customer.getId(), action, key
        )) {
            throw duplicateKey();
        }
    }

    private String searchQuery(String value) {
        if (value == null) {
            throw invalidSearch();
        }
        String query = value.trim();
        if (query.startsWith("$")) {
            query = query.substring(1).trim();
        }
        if (query.length() < properties.safeMinimumQueryLength()
                || query.length() > 100) {
            throw invalidSearch();
        }
        return query;
    }

    private String phoneSearchValue(String query) {
        String digits = query.replaceAll("[^0-9]", "");
        return digits.isBlank() ? "no-phone-match" : digits;
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1) {
            throw new RewardsAdminException(
                    ApiErrorCode.REWARDS_INVALID_PAGINATION, HttpStatus.BAD_REQUEST
            );
        }
        return PageRequest.of(page, Math.min(size, properties.safeMaxPageSize()));
    }

    private boolean reversible(GemsLedgerEntry entry) {
        return entry.getStatus() == GemsLedgerEntryStatus.AVAILABLE
                && switch (entry.getEntryType()) {
                    case DAILY_CHECK_IN, STREAK_MILESTONE, MANUAL_ADJUSTMENT -> true;
                    case REDEMPTION, EXPIRATION, REVERSAL -> false;
                };
    }

    private long balance(UUID customerId) {
        return ledgerRepository.availableBalanceForCustomer(
                customerId, GemsLedgerEntryStatus.AVAILABLE
        );
    }

    private LocalDate businessDate() {
        return RewardsService.businessDate(clock);
    }

    private String ledgerKey(StaffUser staff, String action, String key) {
        UUID requestReference = UUID.nameUUIDFromBytes((action + ":" + key)
                .getBytes(StandardCharsets.UTF_8));
        return "admin:" + staff.getId() + ":" + action + ":" + requestReference;
    }

    private boolean validNote(String note) {
        return note != null && !note.isBlank() && note.trim().length() <= 500;
    }

    private boolean validKey(String key) {
        return key != null && key.trim().matches("[A-Za-z0-9][A-Za-z0-9._:-]{7,159}");
    }

    private RewardsAdminException invalidSearch() {
        return new RewardsAdminException(
                ApiErrorCode.REWARDS_ADMIN_INVALID_SEARCH_QUERY, HttpStatus.BAD_REQUEST
        );
    }

    private RewardsAdminException customerNotFound() {
        return new RewardsAdminException(
                ApiErrorCode.REWARDS_ADMIN_CUSTOMER_NOT_FOUND, HttpStatus.NOT_FOUND
        );
    }

    private RewardsAdminException invalidAdjustment() {
        return new RewardsAdminException(
                ApiErrorCode.REWARDS_ADMIN_INVALID_ADJUSTMENT,
                HttpStatus.UNPROCESSABLE_CONTENT
        );
    }

    private RewardsAdminException duplicateKey() {
        return new RewardsAdminException(
                ApiErrorCode.REWARDS_ADMIN_DUPLICATE_IDEMPOTENCY_KEY,
                HttpStatus.CONFLICT
        );
    }

    private RewardsAdminException alreadyReversed() {
        return new RewardsAdminException(
                ApiErrorCode.REWARDS_ADMIN_REVERSAL_ALREADY_EXISTS,
                HttpStatus.CONFLICT
        );
    }
}
