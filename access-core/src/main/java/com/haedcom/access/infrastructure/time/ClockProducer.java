package com.haedcom.access.infrastructure.time;

import java.time.Clock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

@ApplicationScoped
public class ClockProducer {

    @Produces
    public Clock clock() {
        return Clock.systemUTC();
    }
}
