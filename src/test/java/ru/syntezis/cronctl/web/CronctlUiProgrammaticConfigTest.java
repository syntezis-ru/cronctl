package ru.syntezis.cronctl.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.syntezis.cronctl.CronctlConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = "cronctl.api.public-access=true")
@AutoConfigureMockMvc
class CronctlUiProgrammaticConfigTest {

    @Autowired
    private MockMvc underTest;

    @Test
    void getUi_ProgrammaticUiDisabled_Returns404() throws Exception {
        // Given
        final int expected = 404;

        // When
        final MvcResult actual = underTest.perform(get("/api/cronctl/ui"))
                .andReturn();

        // Then
        assertThat(actual.getResponse().getStatus())
                .isEqualTo(expected);
    }

    @Test
    void getUiStyles_ProgrammaticUiDisabled_Returns404() throws Exception {
        // Given
        final int expected = 404;

        // When
        final MvcResult actual = underTest.perform(get("/api/cronctl/ui/assets/cronctl-ui.css"))
                .andReturn();

        // Then
        assertThat(actual.getResponse().getStatus())
                .isEqualTo(expected);
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        public CronctlConfiguration cronctlConfiguration() {
            return CronctlConfiguration.builder()
                    .uiEnabled(false)
                    .build();
        }
    }
}
