package com.mavela.backend.kyc;

import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.KycStatus;
import com.mavela.backend.error.ApiErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Server-side policy for future regulated customer operations.
 *
 * <p>Money movement, card lifecycle, and account-provisioning endpoints must
 * call {@link #requireVerified(Customer)} before performing their operation.
 * The current application does not expose any such endpoint, so this policy is
 * deliberately not wired to an unrelated route.</p>
 */
@Component
public class KycAccessPolicy {

    public boolean isRestricted(Customer customer) {
        return customer.getKycStatus() != KycStatus.APPROVED;
    }

    public void requireVerified(Customer customer) {
        if (isRestricted(customer)) {
            throw new KycWorkflowException(
                    ApiErrorCode.KYC_ACCESS_RESTRICTED,
                    HttpStatus.FORBIDDEN
            );
        }
    }
}
