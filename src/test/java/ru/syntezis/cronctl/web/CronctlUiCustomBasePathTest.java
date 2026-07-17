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
        "cronctl.api.base-path=/internal/scheduler",
        "cronctl.api.public-access=true"
})
@AutoConfigureMockMvc
class CronctlUiCustomBasePathTest {

    @Autowired
    private MockMvc underTest;

    @Test
    void getUi_CustomBasePath_OperatorPageReturnedOnCustomPath() throws Exception {
        // Given
        final int expected = 200;

        // When
        final MvcResult actual = underTest.perform(get("/internal/scheduler/ui"))
                .andReturn();

        // Then
        assertThat(actual.getResponse().getStatus())
                .isEqualTo(expected);
        assertThat(actual.getResponse().getContentAsString())
                .contains("data-api-base-path=\"/internal/scheduler\"")
                .contains("/internal/scheduler/ui/assets/cronctl-ui.css");
    }

    @Test
    void getUi_CustomBasePath_DefaultPathReturns4xx() throws Exception {
        // Given
        final int expectedLowerBound = 400;
        final int expectedUpperBound = 499;

        // When
        final MvcResult actual = underTest.perform(get("/api/cronctl/ui"))
                .andReturn();

        // Then
        assertThat(actual.getResponse().getStatus())
                .isBetween(expectedLowerBound, expectedUpperBound);
    }

    @Test
    void getUiStyles_CustomBasePath_StylesheetReturnedOnCustomPath() throws Exception {
        // Given
        final int expected = 200;

        // When
        final MvcResult actual = underTest.perform(get("/internal/scheduler/ui/assets/cronctl-ui.css"))
                .andReturn();

        // Then
        assertThat(actual.getResponse().getStatus())
                .isEqualTo(expected);
    }
}
