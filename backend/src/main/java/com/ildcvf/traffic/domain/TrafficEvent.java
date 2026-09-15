package com.ildcvf.traffic.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TrafficEvent(
        String eventId,
        long sequence,
        Instant timestamp,
        String targetService,
        boolean upstreamReached,

        String method,
        String requestPath,
        String queryString,
        Map<String, List<String>> queryParameters,
        Map<String, List<String>> requestHeaders,
        String requestContentType,
        String requestBody,
        int requestBodySizeBytes,
        boolean requestBodyTruncated,

        int responseStatus,
        Map<String, List<String>> responseHeaders,
        String responseContentType,
        String responseBody,
        int responseBodySizeBytes,
        boolean responseBodyTruncated,

        long durationMs
) {

    public TrafficEvent {
        queryParameters = immutableCopy(queryParameters);
        requestHeaders = immutableCopy(requestHeaders);
        responseHeaders = immutableCopy(responseHeaders);
    }

    private static Map<String, List<String>> immutableCopy(
            Map<String, List<String>> source
    ) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> copy = new LinkedHashMap<>();

        source.forEach(
                (key, value) -> copy.put(
                        key,
                        value == null ? List.of() : List.copyOf(value)
                )
        );

        return Map.copyOf(copy);
    }
}