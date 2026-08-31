package com.mavela.backend.admin.services;

import com.mavela.backend.TestcontainersConfiguration;
import com.mavela.backend.admin.auth.AdminPermission;
import com.mavela.backend.admin.staff.StaffUser;
import com.mavela.backend.admin.staff.StaffUserRepository;
import com.mavela.backend.admin.staff.StaffUserStatus;
import com.mavela.backend.auth.AccessTokenService;
import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import com.mavela.backend.error.ApiErrorCode;
import com.mavela.backend.services.catalogue.ServiceAvailability;
import com.mavela.backend.services.catalogue.ServicesCatalogueException;
import com.mavela.backend.services.catalogue.ServicesCatalogueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "mavela.admin.auth.enabled=true",
        "mavela.admin.auth.issuer-uri=https://cognito.example.test/staff-pool",
        "mavela.admin.auth.client-id=staff-client-id"
})
@AutoConfigureMockMvc
class ServicesCatalogueAdminIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServicesCatalogueAdminService adminService;

    @Autowired
    private ServicesCatalogueService customerService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private ServiceCatalogueAdminAuditEventRepository auditRepository;

    @Autowired
    private AccessTokenService accessTokenService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @MockitoBean(name = "adminJwtDecoder")
    private JwtDecoder adminJwtDecoder;

    @AfterEach
    void cleanData() {
        SecurityContextHolder.clearContext();
        jdbcTemplate.update("DELETE FROM service_catalogue_admin_audit_events");
        jdbcTemplate.update("""
                UPDATE service_catalogue_entries
                SET availability = 'COMING_SOON', customer_visible = TRUE
                """);
        staffUserRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    void v20AddsVersionVisibilityAndImmutableAuditSchema() {
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM flyway_schema_history
                WHERE version = '20' AND success = TRUE
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name = 'service_catalogue_admin_audit_events'
                """, Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'service_catalogue_entries'
                  AND column_name IN ('customer_visible', 'version')
                """, Integer.class));
    }

    @Test
    void populatedV19CatalogueUpgradesToSafeNonLiveV20State() {
        String schema = "services_v20_upgrade_" + UUID.randomUUID()
                .toString().replace("-", "");
        try {
            migrateSchema(schema, "19");
            jdbcTemplate.update("""
                    UPDATE %s.service_catalogue_entries
                    SET availability = 'AVAILABLE',
                        provider_key = 'legacy-test-provider',
                        provider_display_name = 'Legacy Test Provider'
                    WHERE service_code = 'airtime'
                    """.formatted(schema));

            migrateSchema(schema, "20");

            assertEquals(6, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM %s.service_catalogue_entries
                    """.formatted(schema), Integer.class));
            assertEquals(0, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM %s.service_catalogue_entries
                    WHERE availability = 'AVAILABLE'
                    """.formatted(schema), Integer.class));
            assertEquals(6, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM %s.service_catalogue_entries
                    WHERE customer_visible = TRUE
                    """.formatted(schema), Integer.class));
            assertEquals(1, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM information_schema.tables
                    WHERE table_schema = ?
                      AND table_name = 'service_catalogue_admin_audit_events'
                    """, Integer.class, schema));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void adminRoutesRejectUnauthenticatedCustomerAndUntrustedStaffTokens()
            throws Exception {
        mockMvc.perform(get("/api/v1/admin/services/catalogue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("ADMIN_AUTHENTICATION_REQUIRED")));

        Customer customer = customer();
        String customerToken = accessTokenService.issue(customer, Instant.now())
                .value();
        when(adminJwtDecoder.decode(customerToken))
                .thenThrow(new BadJwtException("not a staff token"));
        mockMvc.perform(get("/api/v1/admin/services/catalogue")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("ADMIN_AUTHENTICATION_REQUIRED")));

        StaffUser kycReviewer = staff("kyc-reviewer", StaffUserStatus.ACTIVE);
        stubAdminToken("kyc-token", kycReviewer, "KYC_REVIEWER");
        mockMvc.perform(get("/api/v1/admin/services/catalogue")
                        .header("Authorization", "Bearer kyc-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ADMIN_PERMISSION_DENIED")));

        StaffUser rewardsManager = staff("rewards-manager", StaffUserStatus.ACTIVE);
        stubAdminToken("rewards-token", rewardsManager, "REWARDS_MANAGER");
        mockMvc.perform(get("/api/v1/admin/services/catalogue")
                        .header("Authorization", "Bearer rewards-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ADMIN_PERMISSION_DENIED")));

        StaffUser disabled = staff("disabled-services", StaffUserStatus.DISABLED);
        stubAdminToken("disabled-token", disabled, "SERVICES_MANAGER");
        mockMvc.perform(get("/api/v1/admin/services/catalogue")
                        .header("Authorization", "Bearer disabled-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ADMIN_STAFF_ACCOUNT_INACTIVE")));
    }

    @Test
    void servicesManagerReadsUpdatesAndAuditsOnlySafeMetadata()
            throws Exception {
        StaffUser manager = staff("services-manager", StaffUserStatus.ACTIVE);
        stubAdminToken("services-token", manager, "SERVICES_MANAGER");

        mockMvc.perform(get("/api/v1/admin/services/catalogue")
                        .header("Authorization", "Bearer services-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()", is(6)))
                .andExpect(jsonPath("$[*].serviceCode", contains(
                        "airtime", "data", "regideso", "dstv",
                        "salary-advance", "international-transfers"
                )))
                .andExpect(jsonPath("$[0].customerVisible", is(true)))
                .andExpect(jsonPath("$[0].internalNote").doesNotExist())
                .andExpect(jsonPath("$[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$[0].providerKey").doesNotExist());

        mockMvc.perform(patch("/api/v1/admin/services/catalogue/dstv")
                        .header("Authorization", "Bearer services-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "availability":"UNAVAILABLE",
                                  "customerVisible":false,
                                  "displayOrder":2,
                                  "reasonCode":"OPERATIONAL_AVAILABILITY",
                                  "internalNote":"Temporarily suppress planned service.",
                                  "idempotencyKey":"services-update-0001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceCode", is("dstv")))
                .andExpect(jsonPath("$.availability", is("UNAVAILABLE")))
                .andExpect(jsonPath("$.customerVisible", is(false)))
                .andExpect(jsonPath("$.displayOrder", is(2)))
                .andExpect(jsonPath("$.internalNote").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/services/catalogue/dstv/audit")
                        .header("Authorization", "Bearer services-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action", is("CONFIGURATION_UPDATED")))
                .andExpect(jsonPath("$.content[0].staffDisplayName", is("services-manager")))
                .andExpect(jsonPath("$.content[0].internalNote").doesNotExist())
                .andExpect(jsonPath("$.content[0].idempotencyKey").doesNotExist());

        Customer customer = customer();
        assertFalse(customerService.getCatalogue(customer.getId()).stream()
                .anyMatch(entry -> entry.serviceCode().equals("dstv")));
        assertThrows(ServicesCatalogueException.class,
                () -> customerService.getService(customer.getId(), "dstv"));

        mockMvc.perform(post(
                        "/api/v1/admin/services/catalogue/dstv/purchase")
                        .header("Authorization", "Bearer services-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void availableIsRejectedAndIdempotencyMakesRetriesSafe()
            throws Exception {
        StaffUser manager = staff("idempotent-services", StaffUserStatus.ACTIVE);
        stubAdminToken("idempotent-token", manager, "SERVICES_MANAGER");

        String supportedUpdate = """
                {
                  "availability":"UNAVAILABLE",
                  "customerVisible":true,
                  "displayOrder":1,
                  "reasonCode":"COMPLIANCE_REVIEW",
                  "internalNote":"Staff-only planned-state update.",
                  "idempotencyKey":"services-retry-0001"
                }
                """;
        mockMvc.perform(patch("/api/v1/admin/services/catalogue/airtime")
                        .header("Authorization", "Bearer idempotent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supportedUpdate))
                .andExpect(status().isOk());
        mockMvc.perform(patch("/api/v1/admin/services/catalogue/airtime")
                        .header("Authorization", "Bearer idempotent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supportedUpdate))
                .andExpect(status().isOk());
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM service_catalogue_admin_audit_events
                WHERE service_code = 'airtime' AND action = 'CONFIGURATION_UPDATED'
                """, Integer.class));

        mockMvc.perform(patch("/api/v1/admin/services/catalogue/airtime")
                        .header("Authorization", "Bearer idempotent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(supportedUpdate.replace(
                                "customerVisible\":true", "customerVisible\":false")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is(
                        "SERVICES_ADMIN_IDEMPOTENCY_KEY_REUSED"
                )));

        mockMvc.perform(patch("/api/v1/admin/services/catalogue/data")
                        .header("Authorization", "Bearer idempotent-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "availability":"AVAILABLE",
                                  "customerVisible":true,
                                  "displayOrder":2,
                                  "reasonCode":"SERVICE_ROADMAP_UPDATE",
                                  "internalNote":"No live provider exists.",
                                  "idempotencyKey":"services-available-0001"
                                }
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code", is("SERVICE_PROVIDER_NOT_READY")));
    }

    @Test
    void servicesManagerPermissionAndConcurrentRetryRemainSafe() throws Exception {
        StaffUser manager = staff("concurrent-services", StaffUserStatus.ACTIVE);
        authenticate(manager, AdminPermission.SERVICES_READ);
        assertEquals(6, adminService.getCatalogue(manager.getExternalSubject()).size());

        authenticate(manager, AdminPermission.KYC_READ);
        assertThrows(AccessDeniedException.class,
                () -> adminService.getCatalogue(manager.getExternalSubject()));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentUpdate(manager, ready, start));
            var second = executor.submit(() -> concurrentUpdate(manager, ready, start));
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS));
            assertTrue(second.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM service_catalogue_admin_audit_events
                WHERE service_code = 'regideso'
                  AND action = 'CONFIGURATION_UPDATED'
                """, Integer.class));
    }

    private boolean concurrentUpdate(
            StaffUser manager,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        authenticate(manager, AdminPermission.SERVICES_MANAGE);
        ready.countDown();
        try {
            assertTrue(start.await(5, TimeUnit.SECONDS));
            adminService.updateService(manager.getExternalSubject(), "regideso",
                    new UpdateServiceCatalogueRequest(
                            ServiceAvailability.UNAVAILABLE, true, 3,
                            ServiceCatalogueChangeReasonCode.OPERATIONAL_AVAILABILITY,
                            "Concurrent safe retry.", "services-concurrent-0001"
                    ));
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Customer customer() {
        String suffix = String.format("%08d",
                Math.abs(UUID.randomUUID().hashCode()) % 100_000_000L);
        return customerRepository.saveAndFlush(new Customer(
                "+2438" + suffix, null, "Service", "Customer", "en"
        ));
    }

    private StaffUser staff(String subject, StaffUserStatus status) {
        return staffUserRepository.saveAndFlush(new StaffUser(
                subject, subject + "@mavela.test", subject, status
        ));
    }

    private void stubAdminToken(String token, StaffUser staff, String group) {
        when(adminJwtDecoder.decode(token)).thenReturn(adminJwt(
                staff.getExternalSubject(), List.of(group)
        ));
    }

    private Jwt adminJwt(String subject, List<String> groups) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer("https://cognito.example.test/staff-pool")
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("token_use", "access")
                .claim("client_id", "staff-client-id")
                .claim("cognito:groups", groups)
                .build();
    }

    private void authenticate(StaffUser staff, AdminPermission... permissions) {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                staff.getExternalSubject(), "test",
                List.of(permissions).stream()
                        .map(permission -> new SimpleGrantedAuthority(
                                permission.authority()))
                        .toList()
        );
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
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
}
