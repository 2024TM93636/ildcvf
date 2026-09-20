package com.ildcvf.experiment.llm.groq;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record GroqHttpResponse(
        int statusCode,
        String body,
        Map<String, List<String>> headers,
        Duration latency
) {

    public GroqHttpResponse {
        if (statusCode < 100 || statusCode > 599) {
            throw new IllegalArgumentException(
                    "statusCode must be a valid HTTP status");
        }

        Objects.requireNonNull(
                body,
                "body must not be null");

        headers =
                headers == null
                        ? Map.of()
                        : Map.copyOf(headers);

        Objects.requireNonNull(
                latency,
                "latency must not be null");

        if (latency.isNegative()) {
            throw new IllegalArgumentException(
                    "latency must not be negative");
        }
    }
}