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
 * Normal local customer development must start with no Cognito configuration,
 * while the staff API remains explicitly unavailable instead of public.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "mavela.admin.auth.enabled=false")
@AutoConfigureMockMvc
class AdminAuthenticationDisabledIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void disabledAdminAuthenticationFailsClosed() throws Exception {
        mockMvc.perform(get("/api/v1/admin/kyc/applications"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(
                        "ADMIN_AUTHENTICATION_UNAVAILABLE"
                ));
    }
}
