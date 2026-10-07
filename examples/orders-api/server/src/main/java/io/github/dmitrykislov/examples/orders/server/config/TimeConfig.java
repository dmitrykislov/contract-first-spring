package io.github.dmitrykislov.examples.orders.server.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    /** Injected into the service so tests can freeze time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
