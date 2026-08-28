/*
 * Hibernate maps @OrderColumn as INTEGER. V17 is already applied to existing
 * development databases, so correct the physical type in a forward-only
 * migration rather than modifying that immutable migration.
 */
ALTER TABLE service_catalogue_input_requirements
    ALTER COLUMN display_order TYPE INTEGER
    USING display_order::INTEGER;
