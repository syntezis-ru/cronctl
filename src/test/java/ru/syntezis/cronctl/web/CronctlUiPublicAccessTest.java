package ru.syntezis.cronctl.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(properties = "cronctl.api.public-access=true")
@AutoConfigureMockMvc
class CronctlUiPublicAccessTest {

    @Autowired
    private MockMvc underTest;

    @Test
    void getUi_PublicAccess_OperatorPageReturned() throws Exception {
        // Given
        final int expected = 200;

        // When
        final MvcResult actual = underTest.perform(get("/api/cronctl/ui"))
                .andReturn();

        // Then
        assertThat(actual.getResponse().getStatus())
                .isEqualTo(expected);
        assertThat(actual.getResponse().getContentAsString())
                .contains("What runs next")
                .contains("data-api-base-path=\"/api/cronctl\"")
                .contains("/api/cronctl/ui/assets/cronctl-ui.css")
                .contains("/api/cronctl/ui/assets/cronctl-ui.js");
    }

    @Test
    void getUiStyles_PublicAccess_StylesheetReturned() throws Exception {
        // Given
        final int expected = 200;

        // When
        final MvcResult actual = underTest.perform(get("/api/cronctl/ui/assets/cronctl-ui.css"))
                .andReturn();

        // Then
        assertThat(actual.getResponse().getStatus())
                .isEqualTo(expected);
        assertThat(actual.getResponse().getContentType())
                .startsWith("text/css");
        assertThat(actual.getResponse().getContentAsString())
                .contains("--blueprint: #245ca8")
                .contains("grid-template-columns: subgrid")
                .contains("text-overflow: ellipsis");
    }

    @Test
    void getUiScript_PublicAccess_JavaScriptReturned() throws Exception {
        // Given
        final int expected = 200;

        // When
        final MvcResult actual = underTest.perform(get("/api/cronctl/ui/assets/cronctl-ui.js"))
                .andReturn();

        // Then
        assertThat(actual.getResponse().getStatus())
                .isEqualTo(expected);
        assertThat(actual.getResponse().getContentAsString())
                .contains("execute-async")
                .contains("interrupt=${confirmation.interrupt}")
                .contains("Running or overdue");
    }
}
