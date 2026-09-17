package com.ildcvf.traffic.persistence.identity;

import com.ildcvf.traffic.service.TrafficEventIdentity;
import com.ildcvf.traffic.service.TrafficEventIdentityGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "ildcvf.traffic.repository",
        havingValue = "postgres"
)
public class PostgreSqlTrafficEventIdentityGenerator
        implements TrafficEventIdentityGenerator {

    private static final String NEXT_SEQUENCE_SQL =
            "SELECT nextval('traffic_event_sequence')";

    private final JdbcTemplate jdbcTemplate;

    public PostgreSqlTrafficEventIdentityGenerator(
            JdbcTemplate jdbcTemplate
    ) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TrafficEventIdentity next() {
        Long value = jdbcTemplate.queryForObject(
                NEXT_SEQUENCE_SQL,
                Long.class
        );

        if (value == null) {
            throw new IllegalStateException(
                    "PostgreSQL sequence allocation returned null"
            );
        }

        return new TrafficEventIdentity(
                value,
                "traffic-%08d".formatted(value)
        );
    }
}