package ru.syntezis.cronctl.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import ru.syntezis.cronctl.core.Cronctl;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        mockMvc.perform(post("/api/cronctl/tasks/{taskKey}/execute", "missing.task"))
                .andExpect(status().isNotFound());
    }

    @Test
    void executeTask_PublicAccess_RegisteredId_Returns200() throws Exception {
        String taskKey = cronctl.getAllTasks().get(0).getTaskKey();

        mockMvc.perform(post("/api/cronctl/tasks/{taskKey}/execute", taskKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution_id").isNotEmpty())
                .andExpect(jsonPath("$.task_key").value(taskKey))
                .andExpect(jsonPath("$.source").value("MANUAL_SYNC"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.node_id").isNotEmpty())
                .andExpect(jsonPath("$.created_at").isNotEmpty())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
