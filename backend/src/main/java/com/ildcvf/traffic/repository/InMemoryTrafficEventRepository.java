package com.ildcvf.traffic.repository;

import com.ildcvf.traffic.domain.TrafficEvent;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
@ConditionalOnProperty(
        name = "ildcvf.traffic.repository",
        havingValue = "memory"
)
public class InMemoryTrafficEventRepository
        implements TrafficEventRepository {

    private final ConcurrentMap<String, TrafficEvent> events =
            new ConcurrentHashMap<>();

    @Override
    public TrafficEvent save(TrafficEvent event) {
        events.put(event.eventId(), event);
        return event;
    }

    @Override
    public List<TrafficEvent> findAll() {
        return events.values()
                .stream()
                .sorted(
                        Comparator.comparingLong(
                                TrafficEvent::sequence
                        )
                )
                .toList();
    }

    @Override
    public List<TrafficEvent> findRecent(int limit) {
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and 20"
            );
        }

        return events.values()
                .stream()
                .sorted(
                        Comparator.comparingLong(
                                        TrafficEvent::sequence
                                ).reversed()
                                .thenComparing(TrafficEvent::eventId)
                )
                .limit(limit)
                .toList();
    }

    @Override
    public Optional<TrafficEvent> findById(
            String eventId
    ) {
        return Optional.ofNullable(
                events.get(eventId)
        );
    }

    @Override
    public void clear() {
        events.clear();
    }
}