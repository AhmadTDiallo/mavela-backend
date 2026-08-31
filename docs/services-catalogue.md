# Mavela Services Catalogue

## Purpose

The customer Services catalogue is a read-only source of truthful product
availability. It provides stable metadata for the Flutter Services hub; it
does not purchase a service, debit a customer, look up a bill, determine loan
eligibility, or create a remittance.

Gems are rewards, not payment funding. Mavela must not debit Gems for any
service in this catalogue.

## Customer API

All routes require a valid Mavela customer bearer token and return
`Cache-Control: no-store`.

| Method | Route | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/services/catalog` | Returns all current services in display order. |
| `GET` | `/api/v1/services/catalog/{serviceCode}` | Returns one catalogue entry. |

The response contains only:

- `serviceCode`
- `category`
- `availability`
- `providerDisplayName` when a genuine provider is configured
- `supportedCurrencies`
- `inputRequirements`
- `minimumAmount` and `maximumAmount` only when a future live product has
  genuine server-provided limits
- `updatedAt`

It never returns customer balances, accounts, card numbers, database IDs,
provider credentials, staff notes, products, prices, tokens, or provider
responses. Unknown service codes return an RFC Problem Detail with
`SERVICES_CATALOGUE_ITEM_NOT_FOUND`.

## Current services

The seeded entries are intentionally non-live and use `COMING_SOON`:

| Service code | Category | Future input shape |
| --- | --- | --- |
| `airtime` | `AIRTIME` | `PHONE_NUMBER` |
| `data` | `DATA_BUNDLE` | `PHONE_NUMBER` |
| `regideso` | `UTILITY_BILL` | `METER_NUMBER` |
| `dstv` | `TV_SUBSCRIPTION` | `SMART_CARD_NUMBER` |
| `salary-advance` | `SALARY_ADVANCE` | none until an approved partner defines it |
| `international-transfers` | `INTERNATIONAL_REMITTANCE` | none until an approved partner defines it |

`COMING_SOON` means no provider/funding path exists today. `UNAVAILABLE`
means a known service cannot be used at the moment. The current customer and
staff catalogue implementation deliberately blocks `AVAILABLE` at the database
and API layers. A future provider/payment architecture must be introduced in a
separate reviewed migration and command module before any service can become
live.

## Prerequisites for live services

No current entry is live. Before introducing a command endpoint, Mavela needs:

- **Airtime and data:** approved telecom partners, server-side product and
  price validation, account funding, provider confirmation and reconciliation.
- **REGIDESO:** an approved utility integration, safe meter validation,
  confirmed bill/amount data, funding, provider confirmation and
  reconciliation.
- **DStv:** an approved subscription partner, safe smart-card validation,
  product/price confirmation, funding, provider confirmation and
  reconciliation.
- **Salary advance:** a licensed/commercial lending partner, explicit
  eligibility policy, affordability/compliance controls, product approval,
  funding and legally compliant disclosures. This must not be enabled through
  catalogue configuration alone.
- **MoneyGram, Western Union, or another international-remittance partner:**
  approved commercial contracts, jurisdictional compliance, sanctions/AML
  controls, KYC eligibility, destination validation, funding, provider
  confirmation and reconciliation. International remittance requires separate
  partner and compliance approval.

## Future transaction lifecycle

The `services/provider` and `services/orchestration` packages are boundaries
for a future command module only. A future order flow must require:

1. KYC and product eligibility checks;
2. a connected and available customer funding account;
3. server-side validation of amount and identifiers;
4. provider-specific validation;
5. an idempotency key and a persisted transaction/order state machine;
6. provider confirmation via webhook or safe status polling;
7. a customer-safe receipt;
8. reconciliation; and
9. the required commercial, compliance, and legal approvals.

The catalogue alone must never imply that any of these checks succeeded.
