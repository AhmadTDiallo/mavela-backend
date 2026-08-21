package com.mavela.backend.qswitch;

import java.util.List;

/** Safe default; it never emulates successful provider reads. */
/** @deprecated Superseded by {@link UnavailableQSwitchAccountProvider}. */
@Deprecated
final class UnavailableExternalAccountProvider {

    public List<ExternalAccount> listAccounts(
            ExternalCustomerReference customer
    ) {
        throw unavailable();
    }

    public ExternalAccountBalance retrieveBalance(
            ExternalCustomerReference customer,
            ExternalAccountReference account
    ) {
        throw unavailable();
    }

    public ExternalTransactionHistory retrieveTransactionHistory(
            ExternalCustomerReference customer,
            ExternalAccountReference account,
            int limit
    ) {
        throw unavailable();
    }

    private QSwitchIntegrationException unavailable() {
        return new QSwitchIntegrationException(
                QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE
        );
    }
}
