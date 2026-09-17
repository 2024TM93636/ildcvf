package com.ildcvf.traffic.persistence.integration;

import com.ildcvf.traffic.domain.TrafficEvent;
import com.ildcvf.traffic.persistence.identity.PostgreSqlTrafficEventIdentityGenerator;
import com.ildcvf.traffic.persistence.repository.PostgreSqlTrafficEventRepository;
import com.ildcvf.traffic.repository.TrafficEventRepository;
import com.ildcvf.traffic.service.TrafficEventIdentity;
import com.ildcvf.traffic.service.TrafficEventIdentityGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
//import org.testcontainers.junit.jupiter.Container;
//import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

//@Testcontainers
@SpringBootTest(
        properties = {
                "ildcvf.traffic.repository=postgres",
                "spring.jpa.hibernate.ddl-auto=validate"
        }
)
class PostgreSqlTrafficPersistenceIntegrationTest {

//    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17")
                    .withDatabaseName("ildcvf_test")
                    .withUsername("ildcvf_test")
                    .withPassword("ildcvf_test");

    @Autowired
    private TrafficEventRepository repository;

    @Autowired
    private TrafficEventIdentityGenerator identityGenerator;

    @BeforeEach
    void clearRows() {
        repository.clear();
    }

    @Test
    void selectsPostgreSqlImplementationsAndPersistsRepresentativeEvent() {
        assertInstanceOf(
                PostgreSqlTrafficEventRepository.class,
                repository
        );

        assertInstanceOf(
                PostgreSqlTrafficEventIdentityGenerator.class,
                identityGenerator
        );

        TrafficEventIdentity identity =
                identityGenerator.next();

        Instant timestamp =
                Instant.parse(
                        "2026-09-17T12:34:56.123456Z"
                );

        TrafficEvent event =
                representativeEvent(
                        identity,
                        timestamp,
                        "/api/residents",
                        Map.of(
                                "status",
                                List.of(
                                        "ACTIVE",
                                        "INACTIVE"
                                ),
                                "page",
                                List.of("1")
                        )
                );

        TrafficEvent saved =
                repository.save(event);

        assertEquals(
                identity.sequence(),
                saved.sequence()
        );

        assertEquals(
                identity.eventId(),
                saved.eventId()
        );

        Optional<TrafficEvent> found =
                repository.findById(
                        identity.eventId()
                );

        assertTrue(found.isPresent());

        TrafficEvent actual =
                found.orElseThrow();

        assertEquals(
                identity.eventId(),
                actual.eventId()
        );

        assertEquals(
                identity.sequence(),
                actual.sequence()
        );

        assertEquals(
                timestamp,
                actual.timestamp()
        );

        assertEquals(
                "resident-api",
                actual.targetService()
        );

        assertTrue(
                actual.upstreamReached()
        );

        assertEquals(
                "GET",
                actual.method()
        );

        assertEquals(
                "/api/residents",
                actual.requestPath()
        );

        assertEquals(
                "status=ACTIVE&status=INACTIVE&page=1",
                actual.queryString()
        );

        assertEquals(
                List.of(
                        "ACTIVE",
                        "INACTIVE"
                ),
                actual.queryParameters()
                        .get("status")
        );

        assertEquals(
                List.of("application/json"),
                actual.requestHeaders()
                        .get("Accept")
        );

        assertEquals(
                List.of(
                        "trace-a",
                        "trace-b"
                ),
                actual.responseHeaders()
                        .get("X-Trace")
        );

        assertEquals(
                200,
                actual.responseStatus()
        );

        assertEquals(
                17L,
                actual.durationMs()
        );
    }

    @Test
    void missingEventReturnsEmpty() {
        Optional<TrafficEvent> result =
                repository.findById(
                        "definitely-not-a-generated-event-id"
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void findAllReturnsAscendingSequenceRegardlessOfPersistenceOrder() {
        TrafficEventIdentity first =
                identityGenerator.next();

        TrafficEventIdentity second =
                identityGenerator.next();

        TrafficEventIdentity third =
                identityGenerator.next();

        repository.save(
                representativeEvent(
                        third,
                        Instant.parse(
                                "2026-09-17T12:00:03Z"
                        ),
                        "/api/residents/3",
                        Map.of()
                )
        );

        repository.save(
                representativeEvent(
                        first,
                        Instant.parse(
                                "2026-09-17T12:00:01Z"
                        ),
                        "/api/residents/1",
                        Map.of()
                )
        );

        repository.save(
                representativeEvent(
                        second,
                        Instant.parse(
                                "2026-09-17T12:00:02Z"
                        ),
                        "/api/residents/2",
                        Map.of()
                )
        );

        List<TrafficEvent> events =
                repository.findAll();

        assertEquals(
                List.of(
                        first.sequence(),
                        second.sequence(),
                        third.sequence()
                ),
                events.stream()
                        .map(TrafficEvent::sequence)
                        .toList()
        );
    }

    @Test
    void postgreSqlIdentityAllocationIsIncreasingAndFormatsEventId() {
        TrafficEventIdentity first =
                identityGenerator.next();

        TrafficEventIdentity second =
                identityGenerator.next();

        assertTrue(
                second.sequence()
                        > first.sequence()
        );

        assertEquals(
                "traffic-%08d".formatted(
                        first.sequence()
                ),
                first.eventId()
        );

        assertEquals(
                "traffic-%08d".formatted(
                        second.sequence()
                ),
                second.eventId()
        );
    }

    @Test
    void clearDeletesRowsWithoutResettingSequence() {
        TrafficEventIdentity beforeClear =
                identityGenerator.next();

        repository.save(
                representativeEvent(
                        beforeClear,
                        Instant.parse(
                                "2026-09-17T13:00:00Z"
                        ),
                        "/api/residents",
                        Map.of()
                )
        );

        assertFalse(
                repository.findAll()
                        .isEmpty()
        );

        repository.clear();

        assertTrue(
                repository.findAll()
                        .isEmpty()
        );

        TrafficEventIdentity afterClear =
                identityGenerator.next();

        assertTrue(
                afterClear.sequence()
                        > beforeClear.sequence()
        );

        assertEquals(
                "traffic-%08d".formatted(
                        afterClear.sequence()
                ),
                afterClear.eventId()
        );
    }

    private TrafficEvent representativeEvent(
            TrafficEventIdentity identity,
            Instant timestamp,
            String requestPath,
            Map<String, List<String>> queryParameters
    ) {
        return new TrafficEvent(
                identity.eventId(),
                identity.sequence(),
                timestamp,
                "resident-api",
                true,

                "GET",
                requestPath,
                "status=ACTIVE&status=INACTIVE&page=1",
                queryParameters,
                Map.of(
                        "Accept",
                        List.of(
                                "application/json"
                        ),
                        "X-Request-Id",
                        List.of(
                                "request-123"
                        )
                ),
                "application/json",
                null,
                0,
                false,

                200,
                Map.of(
                        "Content-Type",
                        List.of(
                                "application/json"
                        ),
                        "X-Trace",
                        List.of(
                                "trace-a",
                                "trace-b"
                        )
                ),
                "application/json",
                """
                {"status":"ok"}
                """.trim(),
                15,
                false,

                17L
        );
    }
}