package com.ildcvf.traffic.persistence.repository;

import com.ildcvf.traffic.domain.TrafficEvent;
import com.ildcvf.traffic.persistence.mapper.TrafficEventMapper;
import com.ildcvf.traffic.repository.TrafficEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(
        name = "ildcvf.traffic.repository",
        havingValue = "postgres"
)
public class PostgreSqlTrafficEventRepository
        implements TrafficEventRepository {

    private final SpringDataTrafficEventJpaRepository jpaRepository;
    private final TrafficEventMapper mapper;

    public PostgreSqlTrafficEventRepository(
            SpringDataTrafficEventJpaRepository jpaRepository,
            TrafficEventMapper mapper
    ) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public TrafficEvent save(TrafficEvent event) {
        return mapper.toDomain(
                jpaRepository.save(
                        mapper.toEntity(event)
                )
        );
    }

    @Override
    public List<TrafficEvent> findAll() {
        return jpaRepository
                .findAllByOrderBySequenceAsc()
                .stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public Optional<TrafficEvent> findById(
            String eventId
    ) {
        return jpaRepository
                .findByEventId(eventId)
                .map(mapper::toDomain);
    }

    @Override
    public void clear() {
        jpaRepository.deleteAllInBatch();
    }

    @Override
    public List<TrafficEvent> findRecent(int limit) {
        if (limit < 1 || limit > 20) {
            throw new IllegalArgumentException(
                    "limit must be between 1 and 20"
            );
        }

        return jpaRepository
                .findByOrderBySequenceDesc(PageRequest.of(0, limit))
                .stream()
                .map(mapper::toDomain)
                .toList();
    }
}