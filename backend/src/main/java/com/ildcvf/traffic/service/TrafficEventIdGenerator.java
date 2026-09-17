package com.ildcvf.traffic.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(
        name = "ildcvf.traffic.repository",
        havingValue = "memory"
)
public class TrafficEventIdGenerator
        implements TrafficEventIdentityGenerator {

    private final AtomicLong sequence =
            new AtomicLong(0);

    @Override
    public TrafficEventIdentity next() {
        long value = sequence.incrementAndGet();

        return new TrafficEventIdentity(
                value,
                "traffic-%08d".formatted(value)
        );
    }

    public void reset() {
        sequence.set(0);
    }
}