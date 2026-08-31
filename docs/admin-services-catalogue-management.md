# Administrator Services Catalogue Management

## Purpose and safety boundary

This module is a staff-only operational catalogue configuration API. It can
change the customer visibility, planned availability, and display order of one
of Mavela's six seeded service definitions. It **does not** create a purchase,
debit, bill lookup, provider activation, loan/salary-advance decision, or
remittance. It never moves customer funds or Mavela Gems.

All `/api/v1/admin/**` routes use the existing separate Cognito staff access
token validation and require an active, pre-provisioned `staff_users` entry
whose `external_subject` matches the token subject. Customer tokens cannot use
these routes.

## Permission mapping

Only the signed Cognito group `SERVICES_MANAGER` grants catalogue permissions:

| Group | Permissions |
| --- | --- |
| `SERVICES_MANAGER` | `services:read`, `services:manage` |
| `KYC_REVIEWER` / `KYC_SUPERVISOR` | none for Services |
| `REWARDS_MANAGER` | none for Services |
| `PLATFORM_ADMIN` | none for Services unless it also has `SERVICES_MANAGER` |

The backend enforces permissions at both the authenticated admin route and
service-method levels. The staff portal must not treat group UI as authority.

## Routes

All responses are `Cache-Control: no-store` and require the admin bearer
scheme declared in OpenAPI.

| Method | Route | Permission | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/v1/admin/services/catalogue` | `services:read` | Read the complete safe operational catalogue. |
| `GET` | `/api/v1/admin/services/catalogue/{serviceCode}` | `services:read` | Read one safe configuration entry. |
| `PATCH` | `/api/v1/admin/services/catalogue/{serviceCode}` | `services:manage` | Update non-live availability, customer visibility, and display order. |
| `GET` | `/api/v1/admin/services/catalogue/{serviceCode}/audit?page=0&size=25` | `services:read` | Read capped immutable safe audit history. |

Read responses contain only:

- `serviceCode`, `category`, `availability`, `customerVisible`, and
  `displayOrder`;
- `providerDisplayName`, `supportedCurrencies`, `inputRequirements`,
  `minimumAmount`, `maximumAmount`, and `updatedAt`.

They never return provider credentials or URLs, raw database IDs, customer
data, account numbers, products/prices, staff internal notes, idempotency keys,
tokens, or raw errors. The audit response returns the safe before/after
configuration, controlled reason, staff display name, action, and timestamp.
It deliberately excludes the internal note and idempotency material.

## Update command

The body must contain every field below:

```json
{
  "availability": "COMING_SOON",
  "customerVisible": true,
  "displayOrder": 2,
  "reasonCode": "SERVICE_ROADMAP_UPDATE",
  "internalNote": "Approved planned-catalogue ordering change.",
  "idempotencyKey": "services-catalogue-20260831-0001"
}
```

`reasonCode` is one of:

- `CUSTOMER_COMMUNICATION`
- `OPERATIONAL_AVAILABILITY`
- `SERVICE_ROADMAP_UPDATE`
- `ORDERING_CORRECTION`
- `COMPLIANCE_REVIEW`

Only `COMING_SOON` and `UNAVAILABLE` are valid values. `AVAILABLE` is always
rejected with `SERVICE_PROVIDER_NOT_READY` and is also blocked by the V20
database constraint. Changing the catalogue can therefore never make a service
purchasable.

Display order must be in the current fixed catalogue range. Reordering is
transactional: affected entries are moved into a unique contiguous order under
a catalogue write lock, carry optimistic versions, and create immutable audit
events. Concurrent requests are serialized or safely return a conflict.

## Audit and idempotency

Each successful configuration command records an append-only
`CONFIGURATION_UPDATED` event. If a reorder changes other entries, each gets a
separate append-only `DISPLAY_ORDER_REBALANCED` event so the operational order
is auditable. There are no update or delete API operations for audit rows.

The tuple of staff actor, service, configuration action, and idempotency key is
unique. A retry with the same key and same normalized payload returns safely
without another audit event. Reusing the key with a different payload returns
`SERVICES_ADMIN_IDEMPOTENCY_KEY_REUSED` and changes nothing.

## Customer visibility

`customerVisible=false` removes that entry from both existing authenticated
customer catalogue reads:

- `GET /api/v1/services/catalog`
- `GET /api/v1/services/catalog/{serviceCode}`

The single-item route returns the same safe not-found response used for any
disabled/unavailable entry. Internal notes and audit records are never exposed
by customer APIs.

## Prerequisites for any future live service

Making an actual service available requires a separate future architecture and
approval, including a provider adapter, commercial agreement, safe product and
price validation, customer funding controls, idempotent order state machine,
provider confirmation/reconciliation, required KYC/compliance/legal controls,
and dedicated customer command endpoints. This catalogue-management module is
not that implementation and must not be used as a shortcut to activate one.
