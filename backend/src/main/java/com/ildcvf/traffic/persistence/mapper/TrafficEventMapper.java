package com.ildcvf.traffic.persistence.mapper;

import com.ildcvf.traffic.domain.TrafficEvent;
import com.ildcvf.traffic.persistence.entity.TrafficEventEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public final class TrafficEventMapper {

    TrafficEventMapper() {
    }

    public static TrafficEventEntity toEntity(TrafficEvent domain) {
        return new TrafficEventEntity(
                domain.sequence(),
                domain.eventId(),
                domain.timestamp(),
                domain.targetService(),
                domain.upstreamReached(),
                domain.method(),
                domain.requestPath(),
                domain.queryString(),
                mutableCopy(domain.queryParameters()),
                mutableCopy(domain.requestHeaders()),
                domain.requestContentType(),
                domain.requestBody(),
                domain.requestBodySizeBytes(),
                domain.requestBodyTruncated(),
                domain.responseStatus(),
                mutableCopy(domain.responseHeaders()),
                domain.responseContentType(),
                domain.responseBody(),
                domain.responseBodySizeBytes(),
                domain.responseBodyTruncated(),
                domain.durationMs()
        );
    }

    public TrafficEvent toDomain(TrafficEventEntity entity) {
        return new TrafficEvent(
                entity.getEventId(),
                entity.getSequence(),
                entity.getTimestamp(),
                entity.getTargetService(),
                entity.isUpstreamReached(),
                entity.getMethod(),
                entity.getRequestPath(),
                entity.getQueryString(),
                entity.getQueryParameters(),
                entity.getRequestHeaders(),
                entity.getRequestContentType(),
                entity.getRequestBody(),
                Math.toIntExact(entity.getRequestBodySizeBytes()),
                entity.isRequestBodyTruncated(),
                entity.getResponseStatus(),
                entity.getResponseHeaders(),
                entity.getResponseContentType(),
                entity.getResponseBody(),
                Math.toIntExact(entity.getResponseBodySizeBytes()),
                entity.isResponseBodyTruncated(),
                entity.getDurationMs()
        );
    }

    private static Map<String, List<String>> mutableCopy(
            Map<String, List<String>> source
    ) {
        Map<String, List<String>> copy = new LinkedHashMap<>();

        if (source == null) {
            return copy;
        }

        source.forEach((key, value) ->
                copy.put(
                        key,
                        value == null
                                ? new ArrayList<>()
                                : new ArrayList<>(value)
                )
        );

        return copy;
    }
}