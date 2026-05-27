package ru.syntezis.cronctl.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import ru.syntezis.cronctl.core.Cronctl;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "cronctl.api.public-access=true")
@AutoConfigureMockMvc
class CronctlApiPublicAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Cronctl cronctl;

    @Test
    void getTasks_PublicAccess_NoAuthRequired_Returns200() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void executeTask_PublicAccess_UnknownId_Returns404() throws Exception {
        mockMvc.perform(post("/api/cronctl/tasks/{id}/execute", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void executeTask_PublicAccess_RegisteredId_Returns200() throws Exception {
        UUID id = cronctl.getAllTasks().getFirst().getId();

        mockMvc.perform(post("/api/cronctl/tasks/{id}/execute", id))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
