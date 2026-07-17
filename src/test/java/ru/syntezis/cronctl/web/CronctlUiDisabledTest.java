package ru.syntezis.cronctl.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = {
        "cronctl.api.public-access=true",
        "cronctl.ui.enabled=false"
})
@AutoConfigureMockMvc
class CronctlUiDisabledTest {

    @Autowired
    private MockMvc underTest;

    @Test
    void getUi_UiDisabled_Returns404() throws Exception {
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
    void getUiStyles_UiDisabled_Returns404() throws Exception {
        // Given
        final int expected = 404;

        // When
        final MvcResult actual = underTest.perform(get("/api/cronctl/ui/assets/cronctl-ui.css"))
                .andReturn();

        // Then
        assertThat(actual.getResponse().getStatus())
                .isEqualTo(expected);
    }
}
