package com.ildcvf.traffic.service;

import com.ildcvf.traffic.config.TrafficCaptureProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HeaderPolicyTest {

    @Test
    void shouldCaptureAllowedHeaders() {
        HeaderPolicy policy = createPolicy();

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                "Content-Type",
                "application/json"
        );

        request.addHeader(
                "Accept",
                "application/json"
        );

        var result =
                policy.captureRequestHeaders(
                        request
                );

        assertThat(result)
                .containsKey("Content-Type")
                .containsKey("Accept");
    }

    @Test
    void shouldNeverCaptureSensitiveHeaders() {
        HeaderPolicy policy =
                new HeaderPolicy(
                        new TrafficCaptureProperties(
                                65536,
                                List.of(
                                        "Content-Type",
                                        "Authorization",
                                        "Cookie",
                                        "X-API-Key",
                                        "X-Secret"
                                ),
                                List.of(
                                        "Content-Type",
                                        "Set-Cookie"
                                )
                        )
                );

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                "Content-Type",
                "application/json"
        );

        request.addHeader(
                "Authorization",
                "Bearer secret"
        );

        request.addHeader(
                "Cookie",
                "session=secret"
        );

        request.addHeader(
                "X-API-Key",
                "secret-api-key"
        );

        request.addHeader(
                "X-Secret",
                "secret-value"
        );

        var result =
                policy.captureRequestHeaders(
                        request
                );

        assertThat(result)
                .containsKey("Content-Type");

        assertThat(result)
                .doesNotContainKeys(
                        "Authorization",
                        "Cookie",
                        "X-API-Key",
                        "X-Secret"
                );
    }

    private HeaderPolicy createPolicy() {
        return new HeaderPolicy(
                new TrafficCaptureProperties(
                        65536,
                        List.of(
                                "Accept",
                                "Content-Type"
                        ),
                        List.of(
                                "Content-Type",
                                "Location"
                        )
                )
        );
    }
}