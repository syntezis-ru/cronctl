package ru.syntezis.cronctl.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ru.syntezis.cronctl.CronctlConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CronctlProgrammaticConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getTasks_ProgrammaticSecuredAccess_NoAuth_Returns403() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    void getTasks_ProgrammaticSecuredAccess_WithAuth_Returns200() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks"))
                .andExpect(status().isOk());
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public CronctlConfiguration cronctlConfiguration() {
            return CronctlConfiguration.builder()
                    .apiPublicAccess(false)
                    .build();
        }

    }

}
