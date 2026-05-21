package ru.syntezis.cronctl.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "cronctl.api.base-path=/internal/scheduler",
        "cronctl.api.public-access=true",
        "cronctl.swagger.paths-to-match=/internal/scheduler/**"
})
@AutoConfigureMockMvc
class CronctlCustomBasePathTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getTasks_CustomBasePath_RespondsOnCustomPath_Returns200() throws Exception {
        mockMvc.perform(get("/internal/scheduler/tasks"))
                .andExpect(status().isOk());
    }

    @Test
    void getTasks_CustomBasePath_DefaultPathNotRegistered_Returns4xx() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks"))
                .andExpect(status().is4xxClientError());
    }
}
