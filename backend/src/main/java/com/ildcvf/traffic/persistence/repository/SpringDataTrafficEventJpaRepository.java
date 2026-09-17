package com.ildcvf.traffic.persistence.repository;

import com.ildcvf.traffic.persistence.entity.TrafficEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataTrafficEventJpaRepository
        extends JpaRepository<TrafficEventEntity, Long> {

    List<TrafficEventEntity> findAllByOrderBySequenceAsc();

    Optional<TrafficEventEntity> findByEventId(String eventId);
}