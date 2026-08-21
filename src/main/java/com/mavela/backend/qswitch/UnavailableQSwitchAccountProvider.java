package com.mavela.backend.qswitch;

import java.util.List;

/** Fail-closed provider used when the integration is disabled or incomplete. */
public class UnavailableQSwitchAccountProvider implements ExternalAccountProvider {

    @Override
    public List<ExternalAccount> listAccounts(ExternalCustomerReference customer) {
        throw unavailable();
    }

    @Override
    public ExternalAccountBalance retrieveBalance(
            ExternalCustomerReference customer,
            ExternalAccountReference account
    ) {
        throw unavailable();
    }

    @Override
    public ExternalTransactionHistory retrieveTransactionHistory(
            ExternalCustomerReference customer,
            ExternalAccountReference account,
            int limit
    ) {
        throw unavailable();
    }

    private QSwitchIntegrationException unavailable() {
        return new QSwitchIntegrationException(QSwitchIntegrationErrorCode.INTEGRATION_UNAVAILABLE);
    }
}
