package ru.syntezis.cronctl.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@SpringBootApplication
@EnableScheduling
class CronctlTestApplication {

    @Scheduled(fixedRate = Long.MAX_VALUE)
    public void scheduledTask() {
        // NOSONAR
    }
}
