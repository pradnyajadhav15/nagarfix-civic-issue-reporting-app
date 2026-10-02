package dev.nagarfix.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled background jobs (see DailyJobs and EmailOutbox). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
