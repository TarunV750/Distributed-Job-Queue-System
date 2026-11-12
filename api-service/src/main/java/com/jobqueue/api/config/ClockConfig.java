package com.jobqueue.api.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** Injected everywhere instead of Instant.now() so time is controllable in tests. Always UTC. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
