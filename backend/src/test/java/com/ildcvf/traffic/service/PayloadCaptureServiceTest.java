package com.ildcvf.traffic.service;

import com.ildcvf.traffic.config.TrafficCaptureProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PayloadCaptureServiceTest {

    @Test
    void shouldCaptureJsonPayload() {
        PayloadCaptureService service =
                serviceWithLimit(100);

        byte[] payload =
                "{\"name\":\"Asha\"}"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        var result = service.capture(
                payload,
                "application/json"
        );

        assertThat(result.body())
                .isEqualTo(
                        "{\"name\":\"Asha\"}"
                );

        assertThat(result.sizeBytes())
                .isEqualTo(payload.length);

        assertThat(result.truncated())
                .isFalse();
    }

    @Test
    void shouldHandleEmptyPayload() {
        PayloadCaptureService service =
                serviceWithLimit(100);

        var result = service.capture(
                new byte[0],
                "application/json"
        );

        assertThat(result.body()).isNull();
        assertThat(result.sizeBytes()).isZero();
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void shouldTruncateCapturedPayloadWithoutChangingOriginalSize() {
        PayloadCaptureService service =
                serviceWithLimit(10);

        byte[] payload =
                "{\"message\":\"this is longer\"}"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        var result = service.capture(
                payload,
                "application/json"
        );

        assertThat(result.sizeBytes())
                .isEqualTo(payload.length);

        assertThat(
                result.body()
                        .getBytes(
                                StandardCharsets.UTF_8
                        ).length
        ).isLessThanOrEqualTo(10);

        assertThat(result.truncated())
                .isTrue();
    }

    @Test
    void shouldNotCaptureNonJsonBody() {
        PayloadCaptureService service =
                serviceWithLimit(100);

        byte[] payload =
                "plain text"
                        .getBytes(
                                StandardCharsets.UTF_8
                        );

        var result = service.capture(
                payload,
                "text/plain"
        );

        assertThat(result.body()).isNull();
        assertThat(result.sizeBytes())
                .isEqualTo(payload.length);
        assertThat(result.truncated())
                .isFalse();
    }

    private PayloadCaptureService serviceWithLimit(
            int limit
    ) {
        TrafficCaptureProperties properties =
                new TrafficCaptureProperties(
                        limit,
                        List.of("Content-Type"),
                        List.of("Content-Type")
                );

        return new PayloadCaptureService(
                properties
        );
    }
}