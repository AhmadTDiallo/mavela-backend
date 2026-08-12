package com.mavela.backend;

import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.customer.Gender;
import com.mavela.backend.customer.KycStatus;
import com.mavela.backend.kyc.KycApplicationRepository;
import com.mavela.backend.kyc.KycApplicationResponse;
import com.mavela.backend.kyc.KycApplicationService;
import com.mavela.backend.kyc.KycWorkflowException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class KycApplicationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private KycApplicationRepository applicationRepository;

    @Autowired
    private KycApplicationService applicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanDatabase() {
        applicationRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void v11AppliesSuccessfully() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '11' AND success = TRUE
                """,
                Integer.class
        );

        assertEquals(1, count);
    }

    @Test
    void v11UpgradesPopulatedV8DataWithoutInvalidatingLegacyActiveCustomers() {
        String schema = "kyc_upgrade_" + UUID.randomUUID()
                .toString()
                .replace("-", "");

        try {
            migrateSchema(schema, "8");

            UUID notStartedCustomerId = UUID.randomUUID();
            UUID pendingCustomerId = UUID.randomUUID();
            UUID verifiedCustomerId = UUID.randomUUID();

            insertLegacyActiveCustomer(
                    schema,
                    notStartedCustomerId,
                    "+243810000001",
                    "NOT_STARTED"
            );
            insertLegacyActiveCustomer(
                    schema,
                    pendingCustomerId,
                    "+243810000002",
                    "PENDING"
            );
            insertLegacyActiveCustomer(
                    schema,
                    verifiedCustomerId,
                    "+243810000003",
                    "VERIFIED"
            );

            migrateSchema(schema, "11");

            assertEquals(
                    "NOT_STARTED",
                    customerKycStatus(schema, notStartedCustomerId)
            );
            assertEquals(
                    "SUBMITTED",
                    customerKycStatus(schema, pendingCustomerId)
            );
            assertEquals(
                    "APPROVED",
                    customerKycStatus(schema, verifiedCustomerId)
            );
            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM %s.flyway_schema_history
                            WHERE version = '11' AND success = TRUE
                            """.formatted(schema),
                            Integer.class
                    )
            );
            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*)
                            FROM information_schema.tables
                            WHERE table_schema = ?
                              AND table_name = 'kyc_applications'
                            """,
                            Integer.class,
                            schema
                    )
            );
            assertEquals(
                    false,
                    jdbcTemplate.queryForObject(
                            """
                            SELECT convalidated
                            FROM pg_constraint
                            WHERE conname = 'chk_customers_active_username'
                              AND conrelid = (? || '.customers')::regclass
                            """,
                            Boolean.class,
                            schema
                    )
            );

            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update(
                            """
                            UPDATE %s.customers
                            SET first_name = 'Updated Legacy Customer'
                            WHERE id = ?
                            """.formatted(schema),
                            notStartedCustomerId
                    )
            );
            assertThrows(
                    DataIntegrityViolationException.class,
                    () -> jdbcTemplate.update(
                            """
                            INSERT INTO %s.customers (
                                id,
                                phone_number,
                                first_name,
                                last_name,
                                preferred_locale,
                                status,
                                kyc_status,
                                created_at,
                                updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """.formatted(schema),
                            UUID.randomUUID(),
                            "+243810000004",
                            "New",
                            "Customer",
                            "en",
                            "ACTIVE",
                            "NOT_STARTED"
                    )
            );
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void completeProfileCustomerCanCreateAttemptOneAndStartKyc() {
        Customer customer = saveCompleteCustomer();

        KycApplicationResponse response = applicationService.startApplication(
                customer.getId()
        );

        assertNotNull(response.id());
        assertEquals(1, response.attemptNumber());
        assertEquals(KycStatus.IN_PROGRESS, response.status());
        assertEquals(
                KycStatus.IN_PROGRESS,
                customerRepository.findById(customer.getId())
                        .orElseThrow()
                        .getKycStatus()
        );
        assertEquals(
                KycStatus.IN_PROGRESS,
                applicationRepository.findById(response.id())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void incompleteProfileCustomerReceivesProfileIncomplete() {
        Customer customer = customerRepository.saveAndFlush(
                newCustomer("incomplete")
        );

        KycWorkflowException exception = assertThrows(
                KycWorkflowException.class,
                () -> applicationService.startApplication(customer.getId())
        );

        assertEquals(
                "KYC_PROFILE_INCOMPLETE",
                exception.getCode().name()
        );
    }

    @Test
    void secondApplicationCannotBeStartedForTheSameCustomer() {
        Customer customer = saveCompleteCustomer();
        applicationService.startApplication(customer.getId());

        KycWorkflowException exception = assertThrows(
                KycWorkflowException.class,
                () -> applicationService.startApplication(customer.getId())
        );

        assertEquals(
                "KYC_APPLICATION_ALREADY_EXISTS",
                exception.getCode().name()
        );
    }

    @Test
    void getCurrentReturnsOnlyTheAuthenticatedCustomersApplication()
            throws Exception {
        Customer customer = saveCompleteCustomer();
        KycApplicationResponse started = applicationService.startApplication(
                customer.getId()
        );

        mockMvc.perform(
                        get("/api/v1/kyc/applications/current")
                                .with(authenticatedAs(customer.getId()))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(started.id().toString())))
                .andExpect(jsonPath("$.attemptNumber", is(1)))
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));
    }

    @Test
    void customerCannotRetrieveAnotherCustomersApplication()
            throws Exception {
        Customer owner = saveCompleteCustomer();
        applicationService.startApplication(owner.getId());
        Customer otherCustomer = saveCompleteCustomer();

        mockMvc.perform(
                        get("/api/v1/kyc/applications/current")
                                .with(authenticatedAs(otherCustomer.getId()))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(
                        "$.code",
                        is("KYC_APPLICATION_NOT_FOUND")
                ));
    }

    @Test
    void getCurrentReturnsApplicationNotFoundWhenNoneExists()
            throws Exception {
        Customer customer = saveCompleteCustomer();

        mockMvc.perform(
                        get("/api/v1/kyc/applications/current")
                                .with(authenticatedAs(customer.getId()))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath(
                        "$.code",
                        is("KYC_APPLICATION_NOT_FOUND")
                ));
    }

    @Test
    void endpointsRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(post("/api/v1/kyc/applications"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/kyc/applications/current"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void startEndpointCreatesTheAuthenticatedCustomersApplication()
            throws Exception {
        Customer customer = saveCompleteCustomer();

        mockMvc.perform(
                        post("/api/v1/kyc/applications")
                                .contentType(MediaType.APPLICATION_JSON)
                                .with(authenticatedAs(customer.getId()))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNumber", is(1)))
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));
    }

    private Customer saveCompleteCustomer() {
        Customer customer = newCustomer(UUID.randomUUID().toString());
        customer.updateProfile(
                "Ada",
                "Lovelace",
                "en",
                LocalDate.of(1995, 4, 12),
                "CD",
                Gender.FEMALE,
                "1 Mavela Avenue",
                "Kinshasa",
                "Kinshasa",
                Instant.now()
        );
        return customerRepository.saveAndFlush(customer);
    }

    private void migrateSchema(String schema, String targetVersion) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .target(targetVersion)
                .load()
                .migrate();
    }

    private void insertLegacyActiveCustomer(
            String schema,
            UUID customerId,
            String phoneNumber,
            String kycStatus
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO %s.customers (
                    id,
                    phone_number,
                    first_name,
                    last_name,
                    preferred_locale,
                    status,
                    kyc_status,
                    created_at,
                    updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """.formatted(schema),
                customerId,
                phoneNumber,
                "Legacy",
                "Customer",
                "en",
                "ACTIVE",
                kycStatus
        );
    }

    private String customerKycStatus(String schema, UUID customerId) {
        return jdbcTemplate.queryForObject(
                "SELECT kyc_status FROM %s.customers WHERE id = ?"
                        .formatted(schema),
                String.class,
                customerId
        );
    }

    private Customer newCustomer(String suffix) {
        String digits = String.valueOf(Math.abs(suffix.hashCode()));
        String phoneNumber = "+2438" + String.format(
                "%08d",
                Long.parseLong(digits) % 100000000L
        );
        return new Customer(
                phoneNumber,
                null,
                "Ada",
                "Lovelace",
                "en"
        );
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor
    authenticatedAs(UUID customerId) {
        return jwt().jwt(jwt -> jwt.subject(customerId.toString()));
    }
}
