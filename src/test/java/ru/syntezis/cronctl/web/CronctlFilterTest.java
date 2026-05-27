package ru.syntezis.cronctl.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.web.servlet.MockMvc;
import ru.syntezis.cronctl.annotation.CronctlTask;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CronctlFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getScheduledTasks_NoFilter_ReturnsAllTasks() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.tasks", hasSize(4)));
    }

    @Test
    void getScheduledTasks_GroupFilter_ReturnsOnlyMatchingGroup() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks").param("group", "alpha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.tasks", hasSize(2)))
                .andExpect(jsonPath("$.tasks[*].group", everyItem(equalTo("alpha"))));
    }

    @Test
    void getScheduledTasks_GroupFilter_NonExistentGroup_ReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks").param("group", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.tasks", hasSize(0)));
    }

    @Test
    void getScheduledTasks_TagFilter_ReturnsOnlyMatchingTag() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks").param("tag", "critical"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.tasks", hasSize(2)));
    }

    @Test
    void getScheduledTasks_TagFilter_NonExistentTag_ReturnsEmpty() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks").param("tag", "nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.tasks", hasSize(0)));
    }

    @Test
    void getScheduledTasks_GroupAndTagFilter_ReturnsIntersection() throws Exception {
        mockMvc.perform(get("/api/cronctl/tasks").param("group", "alpha").param("tag", "report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.tasks", hasSize(1)))
                .andExpect(jsonPath("$.tasks[0].group").value("alpha"))
                .andExpect(jsonPath("$.tasks[0].tags", hasItem("report")));
    }

    @TestConfiguration
    static class FilterTestConfig {

        @Bean
        public FilterScheduler filterScheduler() {
            return new FilterScheduler();
        }

    }

    static class FilterScheduler {

        @CronctlTask(label = "Alpha Sync", group = "alpha", tags = {"sync", "critical"})
        @Scheduled(fixedRate = Long.MAX_VALUE)
        public void alphaSyncTask() {}

        @CronctlTask(label = "Beta Report", group = "beta", tags = {"report"})
        @Scheduled(fixedRate = Long.MAX_VALUE)
        public void betaReportTask() {}

        @CronctlTask(label = "Alpha Report", group = "alpha", tags = {"report", "critical"})
        @Scheduled(fixedRate = Long.MAX_VALUE)
        public void alphaReportTask() {}

    }

}
