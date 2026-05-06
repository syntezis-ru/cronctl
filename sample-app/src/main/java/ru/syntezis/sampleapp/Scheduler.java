package ru.syntezis.sampleapp;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Scheduler {

    @Scheduled(cron = "${schedule.cron.job-a}")
    public void runAJob() {
        System.out.println("A job running");
    }
}
