package com.mavela.backend.admin.services;

import com.mavela.backend.admin.auth.AdminPermission;
import com.mavela.backend.admin.staff.StaffUser;
import com.mavela.backend.admin.staff.StaffUserService;
import com.mavela.backend.error.ApiErrorCode;
import com.mavela.backend.services.catalogue.ServiceAvailability;
import com.mavela.backend.services.catalogue.ServiceCatalogueEntry;
import com.mavela.backend.services.catalogue.ServiceCatalogueRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Staff-only configuration for catalogue presentation. It deliberately has no
 * provider command, payment, bill lookup, loan, or remittance capability.
 */
@Service
public class ServicesCatalogueAdminService {

    private static final int MAX_PAGE_SIZE = 50;

    private final StaffUserService staffUserService;
    private final ServiceCatalogueRepository catalogueRepository;
    private final ServiceCatalogueAdminAuditEventRepository auditRepository;
    private final EntityManager entityManager;

    public ServicesCatalogueAdminService(
            StaffUserService staffUserService,
            ServiceCatalogueRepository catalogueRepository,
            ServiceCatalogueAdminAuditEventRepository auditRepository,
            EntityManager entityManager
    ) {
        this.staffUserService = staffUserService;
        this.catalogueRepository = catalogueRepository;
        this.auditRepository = auditRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('services:read')")
    public List<AdminServiceCatalogueResponse> getCatalogue(String subject) {
        staffUserService.requireActiveStaff(subject);
        return catalogueRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(AdminServiceCatalogueResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('services:read')")
    public AdminServiceCatalogueResponse getService(
            String subject,
            String serviceCode
    ) {
        staffUserService.requireActiveStaff(subject);
        return catalogueRepository.findByServiceCode(serviceCode)
                .map(AdminServiceCatalogueResponse::from)
                .orElseThrow(this::serviceNotFound);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('services:read')")
    public AdminServiceCatalogueAuditPageResponse getAudit(
            String subject,
            String serviceCode,
            int page,
            int size
    ) {
        staffUserService.requireActiveStaff(subject);
        requireKnownService(serviceCode);
        Page<ServiceCatalogueAdminAuditEvent> result = auditRepository
                .findByServiceCodeOrderByCreatedAtDesc(
                        serviceCode, pageRequest(page, size)
                );
        return new AdminServiceCatalogueAuditPageResponse(
                result.getContent().stream()
                        .map(AdminServiceCatalogueAuditEventResponse::from)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Transactional
    @PreAuthorize("hasAuthority('services:manage')")
    public AdminServiceCatalogueResponse updateService(
            String subject,
            String serviceCode,
            UpdateServiceCatalogueRequest request
    ) {
        StaffUser staff = staffUserService.requireActiveStaff(subject);
        validateOperationalRequest(request);

        /*
         * Lock the small fixed catalogue before moving an entry. This avoids
         * concurrent reorder commands overwriting each other; the target row
         * also carries an optimistic version for accidental stale persistence.
         */
        List<ServiceCatalogueEntry> lockedEntries = catalogueRepository
                .findAllForOperationalUpdate();
        ServiceCatalogueEntry target = lockedEntries.stream()
                .filter(entry -> entry.getServiceCode().equals(serviceCode))
                .findFirst()
                .orElseThrow(this::serviceNotFound);
        validateDisplayOrder(request.displayOrder(), lockedEntries.size());

        String idempotencyKey = request.idempotencyKey().trim();
        String note = request.internalNote().trim();
        String fingerprint = requestFingerprint(request, note);
        var previousRequest = auditRepository
                .findByStaffUser_IdAndServiceCodeAndActionAndIdempotencyKey(
                        staff.getId(),
                        serviceCode,
                        ServiceCatalogueAuditAction.CONFIGURATION_UPDATED,
                        idempotencyKey
                );
        if (previousRequest.isPresent()) {
            if (!previousRequest.get().getRequestFingerprint()
                    .equals(fingerprint)) {
                throw idempotencyKeyReused();
            }
            return AdminServiceCatalogueResponse.from(target);
        }

        Map<String, ServiceConfigurationSnapshot> before = new LinkedHashMap<>();
        for (ServiceCatalogueEntry entry : lockedEntries) {
            before.put(entry.getServiceCode(),
                    ServiceConfigurationSnapshot.from(entry));
        }
        ServiceConfigurationSnapshot targetBefore = before.get(serviceCode);
        short requestedOrder = request.displayOrder().shortValue();

        try {
            /*
             * V20 makes this unique constraint deferrable. The target can be
             * moved into its final slot and all affected rows are shifted as
             * one transaction without transient unique-order violations.
             */
            entityManager.createNativeQuery(
                    "SET CONSTRAINTS uq_service_catalogue_entries_display_order DEFERRED"
            ).executeUpdate();
            target.updateOperationalConfiguration(
                    request.availability(), request.customerVisible(),
                    requestedOrder
            );
            catalogueRepository.saveAndFlush(target);

            if (requestedOrder > targetBefore.displayOrder()) {
                catalogueRepository.shiftOrdersDown(
                        targetBefore.displayOrder(), requestedOrder, serviceCode
                );
            } else if (requestedOrder < targetBefore.displayOrder()) {
                catalogueRepository.shiftOrdersUp(
                        targetBefore.displayOrder(), requestedOrder, serviceCode
                );
            }
            entityManager.flush();
            entityManager.clear();

            List<ServiceCatalogueEntry> after = catalogueRepository
                    .findAllByOrderByDisplayOrderAsc();
            StaffUser auditActor = entityManager.getReference(
                    StaffUser.class, staff.getId()
            );
            for (ServiceCatalogueEntry entry : after) {
                ServiceConfigurationSnapshot original = before.get(
                        entry.getServiceCode()
                );
                ServiceConfigurationSnapshot current =
                        ServiceConfigurationSnapshot.from(entry);
                if (!original.equals(current)) {
                    ServiceCatalogueAuditAction action = entry.getServiceCode()
                            .equals(serviceCode)
                            ? ServiceCatalogueAuditAction.CONFIGURATION_UPDATED
                            : ServiceCatalogueAuditAction.DISPLAY_ORDER_REBALANCED;
                    auditRepository.save(new ServiceCatalogueAdminAuditEvent(
                            auditActor,
                            entry.getServiceCode(),
                            action,
                            original,
                            current,
                            request.reasonCode(),
                            note,
                            idempotencyKey,
                            action == ServiceCatalogueAuditAction
                                    .CONFIGURATION_UPDATED
                                    ? fingerprint
                                    : sha256(fingerprint + "\n"
                                            + entry.getServiceCode())
                    ));
                }
            }
            auditRepository.flush();
            return after.stream()
                    .filter(entry -> entry.getServiceCode().equals(serviceCode))
                    .findFirst()
                    .map(AdminServiceCatalogueResponse::from)
                    .orElseThrow(this::serviceNotFound);
        } catch (DataIntegrityViolationException
                 | ObjectOptimisticLockingFailureException exception) {
            throw concurrencyConflict();
        }
    }

    private void validateOperationalRequest(UpdateServiceCatalogueRequest request) {
        if (request.availability() == ServiceAvailability.AVAILABLE) {
            throw new ServicesCatalogueAdminException(
                    ApiErrorCode.SERVICE_PROVIDER_NOT_READY,
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
    }

    private void validateDisplayOrder(int requestedOrder, int catalogueSize) {
        if (requestedOrder < 1 || requestedOrder > catalogueSize) {
            throw new ServicesCatalogueAdminException(
                    ApiErrorCode.SERVICES_ADMIN_INVALID_DISPLAY_ORDER,
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
    }

    private ServiceCatalogueEntry requireKnownService(String serviceCode) {
        return catalogueRepository.findByServiceCode(serviceCode)
                .orElseThrow(this::serviceNotFound);
    }

    private PageRequest pageRequest(int page, int requestedSize) {
        if (page < 0 || requestedSize < 1 || requestedSize > MAX_PAGE_SIZE) {
            throw new ServicesCatalogueAdminException(
                    ApiErrorCode.SERVICES_ADMIN_INVALID_PAGINATION,
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
        return PageRequest.of(page, requestedSize);
    }

    private ServicesCatalogueAdminException serviceNotFound() {
        return new ServicesCatalogueAdminException(
                ApiErrorCode.SERVICES_ADMIN_CATALOGUE_ITEM_NOT_FOUND,
                HttpStatus.NOT_FOUND
        );
    }

    private ServicesCatalogueAdminException idempotencyKeyReused() {
        return new ServicesCatalogueAdminException(
                ApiErrorCode.SERVICES_ADMIN_IDEMPOTENCY_KEY_REUSED,
                HttpStatus.CONFLICT
        );
    }

    private ServicesCatalogueAdminException concurrencyConflict() {
        return new ServicesCatalogueAdminException(
                ApiErrorCode.SERVICES_ADMIN_CONCURRENCY_CONFLICT,
                HttpStatus.CONFLICT
        );
    }

    private String requestFingerprint(
            UpdateServiceCatalogueRequest request,
            String trimmedNote
    ) {
        return sha256(String.join("\n",
                request.availability().name(),
                request.customerVisible().toString(),
                request.displayOrder().toString(),
                request.reasonCode().name(),
                trimmedNote
        ));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
