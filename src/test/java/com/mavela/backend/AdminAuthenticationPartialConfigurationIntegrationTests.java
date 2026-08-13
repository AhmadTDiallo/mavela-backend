package com.mavela.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * An enabled but incomplete Cognito configuration must be treated exactly as
 * unavailable. It must not start issuer discovery or fall through to the
 * customer JWT chain.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "mavela.admin.auth.enabled=true",
        "mavela.admin.auth.issuer-uri=",
        "mavela.admin.auth.client-id=staff-client-id"
})
@AutoConfigureMockMvc
class AdminAuthenticationPartialConfigurationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void partiallyConfiguredAdminAuthenticationFailsClosed() throws Exception {
        mockMvc.perform(get("/api/v1/admin/kyc/applications"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(
                        "ADMIN_AUTHENTICATION_UNAVAILABLE"
                ));
    }
}
