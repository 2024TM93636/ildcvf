package com.ildcvf.traffic.repository;

import com.ildcvf.traffic.domain.TrafficEvent;

import java.util.List;
import java.util.Optional;

public interface TrafficEventRepository {

    TrafficEvent save(TrafficEvent event);

    List<TrafficEvent> findAll();

    Optional<TrafficEvent> findById(String eventId);

    void clear();

    List<TrafficEvent> findRecent(int limit);
}