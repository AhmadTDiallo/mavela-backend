# Local staff KYC-review provisioning

Customer authentication and staff review use separate identity providers.
Keep `MAVELA_ADMIN_AUTH_ENABLED=false` unless a local Cognito-compatible
issuer is deliberately configured. There is no staff password endpoint and no
seeded administrator.

After an approved staff member has signed in to the configured staff identity
provider once, obtain that identity's stable `sub` from a trusted administrator
tool. A database administrator can then pre-provision the allowlist entry in a
controlled local development database:

```sql
INSERT INTO staff_users (external_subject, email, display_name, status)
VALUES ('<cognito-sub>', 'reviewer@example.test', 'Local KYC Reviewer', 'ACTIVE');
```

Use a non-production test identity and database only. Never place a production
token, password, or personal customer information in this repository. A valid
staff token also needs one of the trusted groups (`KYC_REVIEWER`,
`KYC_SUPERVISOR`, or `PLATFORM_ADMIN`); group membership alone is not enough
without the active allowlist record.
