package com.mavela.backend.qswitch;

import java.util.List;

/**
 * Mavela-owned read-only provider boundary. These types are deliberately not
 * raw QSwitch DTOs and every operation carries the opaque owner reference.
 */
public interface ExternalAccountProvider {

    List<ExternalAccount> listAccounts(ExternalCustomerReference customer);

    ExternalAccountBalance retrieveBalance(
            ExternalCustomerReference customer,
            ExternalAccountReference account
    );

    ExternalTransactionHistory retrieveTransactionHistory(
            ExternalCustomerReference customer,
            ExternalAccountReference account,
            int limit
    );
}
