package ru.syntezis.cronctl.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "cronctl.swagger.public-access=false")
@AutoConfigureMockMvc
class CronctlSwaggerSecuredAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerUi_SecuredAccess_NoAuth_Returns403() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiDocs_SecuredAccess_NoAuth_Returns403() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void swaggerUi_SecuredAccess_WithAuth_Returns200() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void apiDocs_SecuredAccess_WithAuth_Returns200() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk());
    }
}
