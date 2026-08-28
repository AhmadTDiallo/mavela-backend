/*
 * The services catalogue intentionally contains only non-live definitions.
 * It must not be used to initiate purchases, debits, bill inquiries, loans,
 * or remittances. Provider credentials remain in environment configuration,
 * never in this database.
 */
CREATE TABLE service_catalogue_entries (
    service_code VARCHAR(64) PRIMARY KEY,
    category VARCHAR(32) NOT NULL,
    availability VARCHAR(16) NOT NULL,
    provider_key VARCHAR(80),
    provider_display_name VARCHAR(120),
    display_order SMALLINT NOT NULL,
    minimum_amount NUMERIC(19, 4),
    maximum_amount NUMERIC(19, 4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_service_catalogue_entries_code
        CHECK (service_code ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),

    CONSTRAINT chk_service_catalogue_entries_category
        CHECK (category IN (
            'AIRTIME',
            'DATA_BUNDLE',
            'UTILITY_BILL',
            'TV_SUBSCRIPTION',
            'SALARY_ADVANCE',
            'INTERNATIONAL_REMITTANCE'
        )),

    CONSTRAINT chk_service_catalogue_entries_availability
        CHECK (availability IN ('COMING_SOON', 'UNAVAILABLE', 'AVAILABLE')),

    CONSTRAINT chk_service_catalogue_entries_display_order
        CHECK (display_order > 0),

    CONSTRAINT uq_service_catalogue_entries_display_order
        UNIQUE (display_order),

    CONSTRAINT chk_service_catalogue_entries_provider_metadata
        CHECK (
            availability <> 'AVAILABLE'
            OR (
                provider_key IS NOT NULL
                AND btrim(provider_key) <> ''
                AND provider_display_name IS NOT NULL
                AND btrim(provider_display_name) <> ''
            )
        ),

    CONSTRAINT chk_service_catalogue_entries_amount_range
        CHECK (
            (minimum_amount IS NULL AND maximum_amount IS NULL)
            OR (
                minimum_amount IS NOT NULL
                AND maximum_amount IS NOT NULL
                AND minimum_amount >= 0
                AND maximum_amount >= minimum_amount
            )
        )
);

CREATE TABLE service_catalogue_input_requirements (
    service_code VARCHAR(64) NOT NULL,
    input_requirement VARCHAR(32) NOT NULL,
    display_order SMALLINT NOT NULL,

    CONSTRAINT pk_service_catalogue_input_requirements
        PRIMARY KEY (service_code, input_requirement),

    CONSTRAINT fk_service_catalogue_input_requirements_service
        FOREIGN KEY (service_code)
        REFERENCES service_catalogue_entries(service_code)
        ON DELETE RESTRICT,

    CONSTRAINT chk_service_catalogue_input_requirements_value
        CHECK (input_requirement IN (
            'PHONE_NUMBER',
            'METER_NUMBER',
            'SMART_CARD_NUMBER',
            'CUSTOMER_REFERENCE'
        )),

    CONSTRAINT chk_service_catalogue_input_requirements_display_order
        CHECK (display_order >= 0),

    CONSTRAINT uq_service_catalogue_input_requirements_order
        UNIQUE (service_code, display_order)
);

CREATE TABLE service_catalogue_supported_currencies (
    service_code VARCHAR(64) NOT NULL,
    currency_code CHAR(3) NOT NULL,

    CONSTRAINT pk_service_catalogue_supported_currencies
        PRIMARY KEY (service_code, currency_code),

    CONSTRAINT fk_service_catalogue_supported_currencies_service
        FOREIGN KEY (service_code)
        REFERENCES service_catalogue_entries(service_code)
        ON DELETE RESTRICT,

    CONSTRAINT chk_service_catalogue_supported_currencies_code
        CHECK (currency_code ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_service_catalogue_entries_display_order
    ON service_catalogue_entries (display_order);

INSERT INTO service_catalogue_entries (
    service_code,
    category,
    availability,
    provider_key,
    provider_display_name,
    display_order,
    minimum_amount,
    maximum_amount
) VALUES
    ('airtime', 'AIRTIME', 'COMING_SOON', NULL, NULL, 1, NULL, NULL),
    ('data', 'DATA_BUNDLE', 'COMING_SOON', NULL, NULL, 2, NULL, NULL),
    ('regideso', 'UTILITY_BILL', 'COMING_SOON', NULL, NULL, 3, NULL, NULL),
    ('dstv', 'TV_SUBSCRIPTION', 'COMING_SOON', NULL, NULL, 4, NULL, NULL),
    ('salary-advance', 'SALARY_ADVANCE', 'COMING_SOON', NULL, NULL, 5, NULL, NULL),
    ('international-transfers', 'INTERNATIONAL_REMITTANCE', 'COMING_SOON', NULL, NULL, 6, NULL, NULL);

/* These describe future input shapes only; no provider lookup is available. */
INSERT INTO service_catalogue_input_requirements (
    service_code,
    input_requirement,
    display_order
) VALUES
    ('airtime', 'PHONE_NUMBER', 0),
    ('data', 'PHONE_NUMBER', 0),
    ('regideso', 'METER_NUMBER', 0),
    ('dstv', 'SMART_CARD_NUMBER', 0);
