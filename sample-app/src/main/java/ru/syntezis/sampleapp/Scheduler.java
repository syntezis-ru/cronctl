package ru.syntezis.sampleapp;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class Scheduler {

    @Scheduled(cron = "${schedule.cron.job-a}")
    public void runSimpleJob() {
        System.out.println("A job running"); // NOSONAR
    }

    @Scheduled(fixedRate = 10L, timeUnit = TimeUnit.MINUTES)
    public void runHeavyJob() throws InterruptedException {
        System.out.println("Very heavy job started"); // NOSONAR
        Thread.sleep(5000L);
        System.out.println("Very heavy job finished"); // NOSONAR
    }

    @Scheduled(initialDelay = 3L, fixedRate = 10L, timeUnit = TimeUnit.MINUTES)
    private void runThrowingJob() {
        throw new RuntimeException("Some error..."); // NOSONAR
    }
}
