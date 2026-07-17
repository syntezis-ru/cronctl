package ru.syntezis.cronctl.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import ru.syntezis.cronctl.presentation.controller.CronctlUiController;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = {
                CronctlTestApplication.class,
                CronctlUiBeanRegistrationTest.BroadComponentScanConfiguration.class
        },
        properties = "cronctl.api.public-access=true"
)
class CronctlUiBeanRegistrationTest {

    @Autowired
    private ApplicationContext underTest;

    @Test
    void getBeansOfType_BroadComponentScan_SingleCronctlUiControllerRegistered() {
        // Given
        final int expected = 1;

        // When
        final int actual = underTest.getBeansOfType(CronctlUiController.class).size();

        // Then
        assertThat(actual).isEqualTo(expected);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = CronctlUiController.class)
    static class BroadComponentScanConfiguration {

    }

}
