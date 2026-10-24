package com.ildcvf.traffic.persistence.repository;

import com.ildcvf.traffic.persistence.entity.TrafficEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SpringDataTrafficEventJpaRepository
        extends JpaRepository<TrafficEventEntity, Long> {

    List<TrafficEventEntity> findAllByOrderBySequenceAsc();

    Optional<TrafficEventEntity> findByEventId(String eventId);

    List<TrafficEventEntity> findByOrderBySequenceDesc(Pageable pageable);
}