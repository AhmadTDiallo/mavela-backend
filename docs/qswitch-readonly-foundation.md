# QSwitch read-only foundation

This foundation establishes one safe integration boundary:

```text
Flutter customer app -> Mavela Spring Boot -> QSwitch OAuth/API -> Mavela Spring Boot -> Flutter customer app
```

Flutter must never call QSwitch directly, and it must never receive a QSwitch
access token. The backend owns credentials, timeout handling, provider error
mapping, and all future customer-to-provider account mapping.

## Current scope

The implementation introduces Mavela-owned typed operations for:

- listing provider accounts;
- retrieving an account balance;
- retrieving transaction history.

There are intentionally **no HTTP endpoints** yet. The current customer model
has no approved QSwitch customer/account mapping, and QSwitch has not supplied
the exact UAT read endpoint family or response contract. Exposing an endpoint
before both are defined would risk an IDOR flaw or an incorrect financial
integration.

The QSwitch live adapter therefore fails closed without making a network call
until the confirmed read contracts are implemented. This foundation creates no
accounts and supports no transfer, debit, credit, reversal, callback, or
webhook operation.

## Local deterministic mock

Mock data is strictly opt-in and has no real customer information. Add these
values to an untracked local `.env` file, then run `./mvnw spring-boot:run`:

```properties
QSWITCH_ENABLED=true
QSWITCH_MODE=MOCK
```

The `ExternalAccountProvider` then returns fixed, synthetic CDF and USD
accounts, balances, and history entries. No mock endpoint is exposed to
customers and the mock is not enabled unless explicitly requested.

## Staging authentication

The confirmed QSwitch staging token contract is now implemented separately.
See [QSwitch staging authentication](qswitch-staging-auth.md) for the required
`QSWITCH_*` environment variables, exact initial/refresh request bodies,
in-memory token handling, and the DRC country-header rule.

The read-only foundation remains intentionally unavailable for live provider
reads until QSwitch supplies the account/balance/history contracts and Mavela
has a verified customer-to-provider account mapping. Secrets, access tokens,
provider response bodies, and complete customer/provider payloads are never
logged or persisted.

Provider calls use finite connection/response timeouts. The bounded retry
policy is reserved for future idempotent reads and only permits timeouts,
provider-unavailable responses, and rate limiting; it never applies to a
state-changing operation.

## Required QSwitch UAT confirmations before live reads

1. Exact account, balance, and transaction-history endpoint paths, methods,
   request fields, pagination, date/time format, currency representation, and
   response schemas.
2. The approved customer-to-QSwitch-customer and Mavela-account-to-QSwitch-
   account mapping/provisioning model, including ownership/authorization checks.
3. CDF/USD account lifecycle and available-versus-ledger balance semantics.
4. Error semantics, retry-after behavior, idempotency expectations, SLA, and
   support/reconciliation procedures.
5. Separate future contracts for transfers, reversals, webhooks, settlement,
   reconciliation, and dispute handling. None are part of this implementation.

Once these are available, implement raw QSwitch DTOs only inside the live
adapter, map them to `ExternalAccountProvider` types, add protected Mavela
customer endpoints after ownership mapping exists, and add contract tests.
