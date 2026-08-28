package com.mavela.backend.services.catalogue;

import com.mavela.backend.TestcontainersConfiguration;
import com.mavela.backend.customer.Customer;
import com.mavela.backend.customer.CustomerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ServicesCatalogueIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ServicesCatalogueService servicesCatalogueService;

    @AfterEach
    void cleanCustomers() {
        customerRepository.deleteAll();
    }

    @Test
    void servicesCatalogueSchemaMigrationsApplySuccessfully() {
        Integer migrations = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version IN ('17', '18', '19') AND success = TRUE
                """,
                Integer.class
        );

        assertEquals(3, migrations);
    }

    @Test
    void customerCatalogueRoutesRejectUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/api/v1/services/catalog"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/services/catalog/airtime"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedCustomerReceivesSixSafeEntriesInStableOrder() throws Exception {
        Customer customer = saveCustomer();

        mockMvc.perform(get("/api/v1/services/catalog")
                        .with(authenticatedAs(customer.getId())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.length()", is(6)))
                .andExpect(jsonPath("$[*].serviceCode", contains(
                        "airtime",
                        "data",
                        "regideso",
                        "dstv",
                        "salary-advance",
                        "international-transfers"
                )))
                .andExpect(jsonPath("$[*].availability", everyItem(is("COMING_SOON"))))
                .andExpect(jsonPath("$[*].providerDisplayName", everyItem(nullValue())))
                .andExpect(jsonPath("$[*].minimumAmount", everyItem(nullValue())))
                .andExpect(jsonPath("$[*].maximumAmount", everyItem(nullValue())))
                .andExpect(jsonPath("$[0].supportedCurrencies.length()", is(0)))
                .andExpect(jsonPath("$[0].id").doesNotExist())
                .andExpect(jsonPath("$[0].providerKey").doesNotExist())
                .andExpect(jsonPath("$[0].accountNumber").doesNotExist())
                .andExpect(jsonPath("$[0].credentials").doesNotExist());
    }

    @Test
    void detailLookupReturnsOnlySafeMetadata() throws Exception {
        Customer customer = saveCustomer();

        mockMvc.perform(get("/api/v1/services/catalog/regideso")
                        .with(authenticatedAs(customer.getId())))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.serviceCode", is("regideso")))
                .andExpect(jsonPath("$.category", is("UTILITY_BILL")))
                .andExpect(jsonPath("$.availability", is("COMING_SOON")))
                .andExpect(jsonPath("$.inputRequirements", contains("METER_NUMBER")))
                .andExpect(jsonPath("$.providerDisplayName", nullValue()))
                .andExpect(jsonPath("$.minimumAmount", nullValue()))
                .andExpect(jsonPath("$.maximumAmount", nullValue()))
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void unknownCodeReturnsSafeNotFoundProblemDetail() throws Exception {
        Customer customer = saveCustomer();

        mockMvc.perform(get("/api/v1/services/catalog/not-a-service")
                        .with(authenticatedAs(customer.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("SERVICES_CATALOGUE_ITEM_NOT_FOUND")))
                .andExpect(jsonPath("$.detail", is(
                        "The requested service is not available in the Mavela catalogue."
                )));
    }

    @Test
    void disabledServiceCodeReturnsTheSameSafeNotFoundProblemDetail() throws Exception {
        Customer customer = saveCustomer();
        jdbcTemplate.update(
                "UPDATE service_catalogue_entries SET availability = 'UNAVAILABLE' WHERE service_code = 'dstv'"
        );

        try {
            mockMvc.perform(get("/api/v1/services/catalog/dstv")
                            .with(authenticatedAs(customer.getId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code", is("SERVICES_CATALOGUE_ITEM_NOT_FOUND")));
        } finally {
            jdbcTemplate.update(
                    "UPDATE service_catalogue_entries SET availability = 'COMING_SOON' WHERE service_code = 'dstv'"
            );
        }
    }

    @Test
    void unknownAuthenticatedCustomerIsRejectedAndNoServiceIsAvailableByDefault() throws Exception {
        mockMvc.perform(get("/api/v1/services/catalog")
                        .with(authenticatedAs(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("SERVICES_CUSTOMER_NOT_FOUND")));

        Customer customer = saveCustomer();
        List<ServiceCatalogueResponse> catalogue = servicesCatalogueService
                .getCatalogue(customer.getId());

        assertTrue(catalogue.stream().noneMatch(
                service -> service.availability() == ServiceAvailability.AVAILABLE
        ));
    }

    @Test
    void noMutationOrPaymentRouteIsIntroduced() throws Exception {
        Customer customer = saveCustomer();

        mockMvc.perform(post("/api/v1/services/catalog")
                        .with(authenticatedAs(customer.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(post("/api/v1/services/bills/lookup")
                        .with(authenticatedAs(customer.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    private Customer saveCustomer() {
        long suffix = Integer.toUnsignedLong(UUID.randomUUID().hashCode())
                % 100000000L;
        Customer customer = new Customer(
                "+2438" + String.format("%08d", suffix),
                null,
                "Services",
                "Customer",
                "en"
        );
        return customerRepository.saveAndFlush(customer);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor authenticatedAs(
            UUID customerId
    ) {
        return jwt().jwt(jwt -> jwt.subject(customerId.toString()));
    }
}
