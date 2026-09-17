package com.ildcvf.traffic.persistence.mapper;

import com.ildcvf.traffic.domain.TrafficEvent;
import com.ildcvf.traffic.persistence.entity.TrafficEventEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrafficEventMapperTest {

    private final TrafficEventMapper mapper = new TrafficEventMapper();

    @Test
    void shouldPreserveAllFieldsDuringRoundTrip() {
        TrafficEvent original = new TrafficEvent(
                "traffic-00000042",
                42L,
                Instant.parse("2026-09-17T10:15:30Z"),
                "resident-api",
                true,
                "POST",
                "/api/residents",
                "include=active&tag=one&tag=two",
                Map.of(
                        "include", List.of("active"),
                        "tag", List.of("one", "two")
                ),
                Map.of(
                        "Accept", List.of("application/json"),
                        "Content-Type", List.of("application/json")
                ),
                "application/json",
                """
                {"firstName":"Test","lastName":"Resident"}
                """.strip(),
                48,
                false,
                201,
                Map.of(
                        "Content-Type", List.of("application/json"),
                        "Location", List.of("/api/residents/42")
                ),
                "application/json",
                """
                {"id":42,"firstName":"Test","lastName":"Resident"}
                """.strip(),
                52,
                false,
                37L
        );

        TrafficEventEntity entity = mapper.toEntity(original);
        TrafficEvent restored = mapper.toDomain(entity);

        assertEquals(original, restored);
    }

    @Test
    void shouldPreserveNullableFieldsAndEmptyMaps() {
        TrafficEvent original = new TrafficEvent(
                "traffic-00000043",
                43L,
                Instant.parse("2026-09-17T10:16:30Z"),
                "resident-api",
                true,
                "GET",
                "/api/residents",
                null,
                Map.of(),
                Map.of(),
                null,
                null,
                0,
                false,
                204,
                Map.of(),
                null,
                null,
                0,
                false,
                5L
        );

        TrafficEventEntity entity = mapper.toEntity(original);
        TrafficEvent restored = mapper.toDomain(entity);

        assertEquals(original, restored);
        assertEquals(Map.of(), restored.queryParameters());
        assertEquals(Map.of(), restored.requestHeaders());
        assertEquals(Map.of(), restored.responseHeaders());
    }

    @Test
    void shouldRejectRequestBodySizeOutsideIntegerRange() {
        TrafficEventEntity entity = entityWithBodySizes(
                (long) Integer.MAX_VALUE + 1,
                0L
        );

        assertThrows(
                ArithmeticException.class,
                () -> mapper.toDomain(entity)
        );
    }

    @Test
    void shouldRejectResponseBodySizeOutsideIntegerRange() {
        TrafficEventEntity entity = entityWithBodySizes(
                0L,
                (long) Integer.MAX_VALUE + 1
        );

        assertThrows(
                ArithmeticException.class,
                () -> mapper.toDomain(entity)
        );
    }

    private TrafficEventEntity entityWithBodySizes(
            long requestBodySizeBytes,
            long responseBodySizeBytes
    ) {
        return new TrafficEventEntity(
                44L,
                "traffic-00000044",
                Instant.parse("2026-09-17T10:17:30Z"),
                "resident-api",
                true,
                "GET",
                "/api/residents/44",
                null,
                Map.of(),
                Map.of(),
                null,
                null,
                requestBodySizeBytes,
                false,
                200,
                Map.of(),
                null,
                null,
                responseBodySizeBytes,
                false,
                10L
        );
    }
}