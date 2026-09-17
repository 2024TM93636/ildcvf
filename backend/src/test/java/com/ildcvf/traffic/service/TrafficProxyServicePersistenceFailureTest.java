package com.ildcvf.traffic.service;

import com.ildcvf.traffic.client.ForwardedResponse;
import com.ildcvf.traffic.client.ResidentForwardingClient;
import com.ildcvf.traffic.config.ResidentTargetProperties;
import com.ildcvf.traffic.domain.CapturedPayload;
import com.ildcvf.traffic.domain.TrafficEvent;
import com.ildcvf.traffic.repository.TrafficEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TrafficProxyServicePersistenceFailureTest {

    private ResidentForwardingClient forwardingClient;
    private ResidentTargetProperties targetProperties;
    private TrafficEventRepository repository;
    private PayloadCaptureService payloadCaptureService;
    private HeaderPolicy headerPolicy;
    private TrafficEventIdentityGenerator identityGenerator;

    private TrafficProxyService service;

    @BeforeEach
    void setUp() {
        forwardingClient =
                mock(ResidentForwardingClient.class);

        targetProperties =
                mock(ResidentTargetProperties.class);

        repository =
                mock(TrafficEventRepository.class);

        payloadCaptureService =
                mock(PayloadCaptureService.class);

        headerPolicy =
                mock(HeaderPolicy.class);

        identityGenerator =
                mock(TrafficEventIdentityGenerator.class);

        service = new TrafficProxyService(
                forwardingClient,
                targetProperties,
                repository,
                payloadCaptureService,
                headerPolicy,
                identityGenerator
        );

        when(targetProperties.serviceId())
                .thenReturn("resident-api");

        when(identityGenerator.next())
                .thenReturn(
                        new TrafficEventIdentity(
                                999,
                                "traffic-00000999"
                        )
                );

        when(headerPolicy.captureRequestHeaders(any()))
                .thenReturn(Map.of());

        when(headerPolicy.getForwardableRequestHeaders(any()))
                .thenReturn(new HttpHeaders());

        when(headerPolicy.captureResponseHeaders(any()))
                .thenReturn(Map.of());

        when(headerPolicy.getForwardableResponseHeaders(any()))
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        when(payloadCaptureService.capture(
                any(byte[].class),
                nullable(String.class)
        )).thenAnswer(invocation -> {
            byte[] body =
                    invocation.getArgument(0);

            return new CapturedPayload(
                    new String(body, StandardCharsets.UTF_8),
                    body.length,
                    false
            );
        });

        doThrow(
                new RuntimeException(
                        "simulated persistence failure"
                )
        ).when(repository)
                .save(any(TrafficEvent.class));
    }

    @Test
    void preservesSuccessfulUpstreamResponseWhenPersistenceFails() {
        HttpServletRequest request =
                createRequest();

        byte[] upstreamBody =
                """
                {"result":"ok"}
                """.trim()
                        .getBytes(StandardCharsets.UTF_8);

        HttpHeaders upstreamHeaders =
                new HttpHeaders();

        upstreamHeaders.setContentType(
                MediaType.APPLICATION_JSON
        );

        upstreamHeaders.add(
                "X-Test-Header",
                "preserved"
        );

        when(forwardingClient.forward(
                eq(HttpMethod.GET),
                eq("/api/residents"),
                isNull(),
                any(HttpHeaders.class),
                any(byte[].class)
        )).thenReturn(
                new ForwardedResponse(
                        HttpStatus.OK,
                        upstreamHeaders,
                        upstreamBody,
                        true
                )
        );

        ForwardedResponse result =
                service.proxy(
                        request,
                        new byte[0]
                );

        assertEquals(
                HttpStatus.OK,
                result.status()
        );

        assertArrayEquals(
                upstreamBody,
                result.body()
        );

        assertTrue(
                result.upstreamReached()
        );

        assertEquals(
                "preserved",
                result.headers()
                        .getFirst("X-Test-Header")
        );

        verify(repository)
                .save(any(TrafficEvent.class));
    }

    @Test
    void preservesUpstreamBusinessErrorWhenPersistenceFails() {
        HttpServletRequest request =
                createRequest();

        byte[] upstreamBody =
                """
                {"code":"NOT_FOUND"}
                """.trim()
                        .getBytes(StandardCharsets.UTF_8);

        HttpHeaders upstreamHeaders =
                new HttpHeaders();

        upstreamHeaders.setContentType(
                MediaType.APPLICATION_JSON
        );

        when(forwardingClient.forward(
                eq(HttpMethod.GET),
                eq("/api/residents"),
                isNull(),
                any(HttpHeaders.class),
                any(byte[].class)
        )).thenReturn(
                new ForwardedResponse(
                        HttpStatus.NOT_FOUND,
                        upstreamHeaders,
                        upstreamBody,
                        true
                )
        );

        ForwardedResponse result =
                service.proxy(
                        request,
                        new byte[0]
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                result.status()
        );

        assertArrayEquals(
                upstreamBody,
                result.body()
        );

        assertTrue(
                result.upstreamReached()
        );

        verify(repository)
                .save(any(TrafficEvent.class));
    }

    private HttpServletRequest createRequest() {
        HttpServletRequest request =
                mock(HttpServletRequest.class);

        when(request.getRequestURI())
                .thenReturn(
                        "/proxy/api/residents"
                );

        when(request.getQueryString())
                .thenReturn(null);

        when(request.getMethod())
                .thenReturn("GET");

        when(request.getContentType())
                .thenReturn(null);

        when(request.getParameterMap())
                .thenReturn(
                        Map.<String, String[]>of()
                );

        return request;
    }
}