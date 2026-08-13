package com.mavela.backend.admin.staff;

import com.mavela.backend.error.ApiErrorCode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the stable OIDC subject to a pre-provisioned, active staff user.
 * A successful JWT alone never grants data access to the staff API.
 */
@Service
public class StaffUserService {

    private final StaffUserRepository staffUserRepository;

    public StaffUserService(StaffUserRepository staffUserRepository) {
        this.staffUserRepository = staffUserRepository;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public StaffUser requireActiveStaff(String externalSubject) {
        requireAuthenticatedSubject(externalSubject);

        StaffUser staffUser = staffUserRepository
                .findByExternalSubject(externalSubject)
                .orElseThrow(() -> new StaffUserAccessException(
                        ApiErrorCode.ADMIN_STAFF_NOT_PROVISIONED
                ));

        if (!staffUser.isActive()) {
            throw new StaffUserAccessException(
                    ApiErrorCode.ADMIN_STAFF_ACCOUNT_INACTIVE
            );
        }

        return staffUser;
    }

    /**
     * Controllers derive the subject from the validated JWT. Keep this check
     * at the allowlist boundary as well so a future internal caller cannot
     * substitute another active staff member for assignment or audit actor
     * attribution.
     */
    private void requireAuthenticatedSubject(String externalSubject) {
        Authentication authentication = SecurityContextHolder.getContext()
                .getAuthentication();
        if (externalSubject == null || externalSubject.isBlank()
                || authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || !externalSubject.equals(authentication.getName())) {
            throw new StaffUserAccessException(
                    ApiErrorCode.ADMIN_PERMISSION_DENIED
            );
        }
    }
}
