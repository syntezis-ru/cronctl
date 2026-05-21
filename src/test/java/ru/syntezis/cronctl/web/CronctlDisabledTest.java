package ru.syntezis.cronctl.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "cronctl.enabled=false")
@AutoConfigureMockMvc
class CronctlDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void getTasks_CronctlDisabled_EndpointNotRegistered_Returns404() throws Exception {
        // When cronctl is disabled, no controller is registered.
        // Using WithMockUser to bypass management security and confirm no handler exists.
        mockMvc.perform(get("/api/cronctl/tasks"))
                .andExpect(status().isNotFound());
    }
}
