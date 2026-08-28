/*
 * The catalogue entity maps currency codes as String/VARCHAR(3). V17 has
 * already been applied, so align its CHAR(3) column with Hibernate through a
 * forward-only migration.
 */
ALTER TABLE service_catalogue_supported_currencies
    ALTER COLUMN currency_code TYPE VARCHAR(3)
    USING btrim(currency_code);
