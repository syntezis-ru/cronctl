package ru.syntezis.cronctl.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "cronctl.api.public-access=false")
@AutoConfigureMockMvc
class CronctlApiSecuredAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getTasks_SecuredAccess_NoAuth_Returns403() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks"))
                .andExpect(status().isForbidden());
    }

    @Test
    void executeTask_SecuredAccess_NoAuth_Returns403() throws Exception {
        mockMvc.perform(post("/api/cronctl/execute/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getTasks_SecuredAccess_WithAuth_Returns200() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void executeTask_SecuredAccess_WithAuth_UnknownId_Returns404() throws Exception {
        mockMvc.perform(post("/api/cronctl/execute/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
