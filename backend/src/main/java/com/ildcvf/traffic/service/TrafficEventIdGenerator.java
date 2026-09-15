package com.ildcvf.traffic.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class TrafficEventIdGenerator {

    private final AtomicLong sequence =
            new AtomicLong(0);

    public EventIdentity next() {
        long value = sequence.incrementAndGet();

        return new EventIdentity(
                value,
                "traffic-%08d".formatted(value)
        );
    }

    public void reset() {
        sequence.set(0);
    }

    public record EventIdentity(
            long sequence,
            String eventId
    ) {
    }
}