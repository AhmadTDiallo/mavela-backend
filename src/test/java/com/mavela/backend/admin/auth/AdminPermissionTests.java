package com.mavela.backend.admin.auth;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPermissionTests {

    @Test
    void reviewerReceivesOnlyReviewerPermissions() {
        assertThat(AdminPermission.fromTrustedGroups(
                List.of("KYC_REVIEWER")
        )).containsExactlyInAnyOrder(
                AdminPermission.KYC_READ,
                AdminPermission.KYC_CLAIM,
                AdminPermission.KYC_DECIDE
        );
    }

    @Test
    void supervisorReceivesReviewerAndSupervisionPermissions() {
        assertThat(AdminPermission.fromTrustedGroups(
                List.of("KYC_SUPERVISOR")
        )).containsExactlyInAnyOrder(
                AdminPermission.KYC_READ,
                AdminPermission.KYC_CLAIM,
                AdminPermission.KYC_DECIDE,
                AdminPermission.KYC_SUPERVISE
        );
    }

    @Test
    void platformAdminDoesNotImplicitlyReceiveKycPermissions() {
        assertThat(AdminPermission.fromTrustedGroups(
                List.of("PLATFORM_ADMIN")
        )).containsExactly(AdminPermission.STAFF_MANAGE);
    }

    @Test
    void unknownGroupsGrantNoPermissions() {
        assertThat(AdminPermission.fromTrustedGroups(
                List.of("KYC_REVIEWER_LOOKALIKE", "CUSTOMER_ADMIN")
        )).isEqualTo(Set.of());
    }
}
