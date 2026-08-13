package com.mavela.backend.admin.auth;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The only permissions that can be derived from a signed Cognito group
 * claim. No arbitrary role or permission claim is trusted.
 */
public enum AdminPermission {

    KYC_READ("kyc:read"),
    KYC_CLAIM("kyc:claim"),
    KYC_DECIDE("kyc:decide"),
    KYC_SUPERVISE("kyc:supervise"),
    STAFF_MANAGE("staff:manage");

    private static final Set<AdminPermission> REVIEWER_PERMISSIONS = Set.of(
            KYC_READ,
            KYC_CLAIM,
            KYC_DECIDE
    );

    private final String authority;

    AdminPermission(String authority) {
        this.authority = authority;
    }

    public String authority() {
        return authority;
    }

    public static Set<AdminPermission> fromTrustedGroups(
            Collection<String> groups
    ) {
        Set<AdminPermission> permissions = new LinkedHashSet<>();

        if (groups == null) {
            return Set.copyOf(permissions);
        }

        for (String group : groups) {
            if (group == null) {
                continue;
            }

            switch (group) {
                case "KYC_REVIEWER" -> permissions.addAll(
                        REVIEWER_PERMISSIONS
                );
                case "KYC_SUPERVISOR" -> {
                    permissions.addAll(REVIEWER_PERMISSIONS);
                    permissions.add(KYC_SUPERVISE);
                }
                case "PLATFORM_ADMIN" -> permissions.add(STAFF_MANAGE);
                default -> {
                    // Unknown groups deliberately grant no permission.
                }
            }
        }

        return Set.copyOf(permissions);
    }
}
