package com.mavela.backend.rewards;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class RewardsConfiguration {

    @Bean(name = "rewardsClock")
    Clock rewardsClock() {
        return Clock.systemUTC();
    }
}
