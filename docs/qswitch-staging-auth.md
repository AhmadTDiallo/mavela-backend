# QSwitch staging authentication

This module establishes Mavela's server-side QSwitch staging authentication
boundary:

```text
Mavela backend -> QSwitch staging
```

Flutter must never call QSwitch directly and never receives a QSwitch access or
refresh token. There is no public Mavela endpoint for QSwitch token state.

## Scope and current limitation

QSwitch staging authentication is available now. The QSwitch financial
institution has been provisioned, but its general-ledger accounts have not.
This implementation does not create ledger accounts or call any ledger,
balance, account, customer, merchant, transfer, payment, or posting endpoint.
Consequently, it must not be used to claim meaningful balance or ledger-posting
behaviour.

The shared staging environment is non-durable. Never send real customer data to
it.

## Configuration

Keep values in an untracked local `.env` file or a deployment secret manager.
Do not add credentials, token values, or copied staging-document values to
source control.

```properties
QSWITCH_ENABLED=false
QSWITCH_BASE_URL=https://<qswitch-staging-host>
QSWITCH_APP_ID=<application-id>
QSWITCH_APP_TOKEN=<application-token>
QSWITCH_APP_SECRET=<provided-separately>
QSWITCH_FINTECH_ID=<financial-institution-id>
QSWITCH_COUNTRY_CODE=DRC
```

`QSWITCH_APP_SECRET` is required only when `QSWITCH_ENABLED=true`. The module
is disabled by default. An enabled but incomplete or invalid configuration fails
closed and does not call QSwitch. The base URL must use HTTPS.

## Token lifecycle

The initial application token request is:

```text
POST /epp2/fintech/auth/token
Content-Type: application/json
```

Its JSON body contains exactly `app_token` and `app_secret`. It must not include
`grant_type`.

The refresh request is:

```text
POST /epp2/fintech/auth/token/refresh
Content-Type: application/json
```

Its JSON body contains `grant_type` set to `refresh_token`, `app_token`, and the
current `refresh_token`.

The documented access-token lifetime is 240 hours. The backend keeps the access
and refresh token only in a concurrency-safe in-memory cache, refreshes before
expiry, and prevents concurrent refresh stampedes. A failed refresh performs a
fresh initial authentication exchange; it never replays a future state-changing
or money-moving request.

Token values, credentials, raw provider responses, and authorization headers
are never persisted, logged, returned through Mavela APIs, or included in
exception messages.

## Future API requests

The internal `QSwitchAuthenticatedClient` adds:

```text
Authorization: Bearer <access token>
```

For future paths under `/api/**`, it also adds:

```text
x-country-code: DRC
```

Authentication paths under `/epp2/**` do not receive that country header.

## Completing ledger provisioning

Before Mavela can safely implement ledger, account, balance, or payment work,
QSwitch still needs:

1. Nine Mavela general-ledger account numbers for terminal, issuing, acquiring,
   merchant clearing, cash-deposit clearing, settlement clearing, offline
   issuing, settlement, and reconciliation clearing.
2. A confirmed notification email address.
3. A confirmed settlement bank.
4. QSwitch confirmation that ledger provisioning is complete.

Only after those prerequisites and endpoint contracts are confirmed should a
future backend-to-QSwitch integration add typed financial operations. Flutter
must remain outside that integration boundary.
