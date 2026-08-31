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
    void rewardsManagerReceivesOnlyRewardsPermissions() {
        assertThat(AdminPermission.fromTrustedGroups(
                List.of("REWARDS_MANAGER")
        )).containsExactlyInAnyOrder(
                AdminPermission.REWARDS_READ,
                AdminPermission.REWARDS_ADJUST
        );
    }

    @Test
    void kycAndPlatformGroupsDoNotImplicitlyReceiveRewardsPermissions() {
        assertThat(AdminPermission.fromTrustedGroups(List.of(
                "KYC_REVIEWER", "KYC_SUPERVISOR", "PLATFORM_ADMIN"
        ))).doesNotContain(
                AdminPermission.REWARDS_READ,
                AdminPermission.REWARDS_ADJUST
        );
    }

    @Test
    void servicesManagerReceivesOnlyServicesPermissions() {
        assertThat(AdminPermission.fromTrustedGroups(
                List.of("SERVICES_MANAGER")
        )).containsExactlyInAnyOrder(
                AdminPermission.SERVICES_READ,
                AdminPermission.SERVICES_MANAGE
        );
    }

    @Test
    void kycRewardsAndPlatformGroupsDoNotImplicitlyReceiveServicesPermissions() {
        assertThat(AdminPermission.fromTrustedGroups(List.of(
                "KYC_REVIEWER", "KYC_SUPERVISOR", "REWARDS_MANAGER",
                "PLATFORM_ADMIN"
        ))).doesNotContain(
                AdminPermission.SERVICES_READ,
                AdminPermission.SERVICES_MANAGE
        );
    }

    @Test
    void unknownGroupsGrantNoPermissions() {
        assertThat(AdminPermission.fromTrustedGroups(
                List.of("KYC_REVIEWER_LOOKALIKE", "CUSTOMER_ADMIN")
        )).isEqualTo(Set.of());
    }
}
