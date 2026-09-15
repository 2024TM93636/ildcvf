package com.ildcvf.traffic.service;

import com.ildcvf.traffic.client.ForwardedResponse;
import com.ildcvf.traffic.client.ResidentForwardingClient;
import com.ildcvf.traffic.config.ResidentTargetProperties;
import com.ildcvf.traffic.domain.CapturedPayload;
import com.ildcvf.traffic.domain.TrafficEvent;
import com.ildcvf.traffic.repository.TrafficEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class TrafficProxyService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    TrafficProxyService.class
            );

    private static final String PROXY_PREFIX =
            "/proxy";

    private final ResidentForwardingClient forwardingClient;
    private final ResidentTargetProperties targetProperties;
    private final TrafficEventRepository repository;
    private final TrafficEventIdGenerator idGenerator;
    private final PayloadCaptureService payloadCaptureService;
    private final HeaderPolicy headerPolicy;

    public TrafficProxyService(
            ResidentForwardingClient forwardingClient,
            ResidentTargetProperties targetProperties,
            TrafficEventRepository repository,
            TrafficEventIdGenerator idGenerator,
            PayloadCaptureService payloadCaptureService,
            HeaderPolicy headerPolicy
    ) {
        this.forwardingClient = forwardingClient;
        this.targetProperties = targetProperties;
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.payloadCaptureService =
                payloadCaptureService;
        this.headerPolicy = headerPolicy;
    }

    public ForwardedResponse proxy(
            HttpServletRequest request,
            byte[] requestBody
    ) {
        TrafficEventIdGenerator.EventIdentity identity =
                idGenerator.next();

        Instant timestamp = Instant.now();

        long startNanos = System.nanoTime();

        String targetPath =
                extractTargetPath(
                        request.getRequestURI()
                );

        String queryString =
                request.getQueryString();

        HttpMethod method =
                HttpMethod.valueOf(
                        request.getMethod()
                );

        byte[] safeRequestBody =
                requestBody == null
                        ? new byte[0]
                        : requestBody;

        CapturedPayload capturedRequest =
                payloadCaptureService.capture(
                        safeRequestBody,
                        request.getContentType()
                );

        Map<String, List<String>> capturedRequestHeaders =
                headerPolicy.captureRequestHeaders(
                        request
                );

        HttpHeaders forwardedRequestHeaders =
                headerPolicy
                        .getForwardableRequestHeaders(
                                request
                        );

        Map<String, List<String>> queryParameters =
                extractQueryParameters(request);

        ForwardedResponse upstreamResponse;

        try {
            upstreamResponse =
                    forwardingClient.forward(
                            method,
                            targetPath,
                            queryString,
                            forwardedRequestHeaders,
                            safeRequestBody
                    );

        } catch (RestClientException exception) {
            log.warn(
                    "Unable to reach target service {} for {} {}",
                    targetProperties.serviceId(),
                    method,
                    targetPath
            );

            upstreamResponse =
                    createBadGatewayResponse();
        }

        long durationMs =
                TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime()
                                - startNanos
                );

        String responseContentType =
                upstreamResponse.headers()
                        .getFirst(
                                HttpHeaders.CONTENT_TYPE
                        );

        CapturedPayload capturedResponse =
                payloadCaptureService.capture(
                        upstreamResponse.body(),
                        responseContentType
                );

        Map<String, List<String>> capturedResponseHeaders =
                headerPolicy.captureResponseHeaders(
                        upstreamResponse.headers()
                );

        TrafficEvent event = new TrafficEvent(
                identity.eventId(),
                identity.sequence(),
                timestamp,
                targetProperties.serviceId(),
                upstreamResponse.upstreamReached(),

                method.name(),
                targetPath,
                queryString,
                queryParameters,
                capturedRequestHeaders,
                request.getContentType(),
                capturedRequest.body(),
                capturedRequest.sizeBytes(),
                capturedRequest.truncated(),

                upstreamResponse.status().value(),
                capturedResponseHeaders,
                responseContentType,
                capturedResponse.body(),
                capturedResponse.sizeBytes(),
                capturedResponse.truncated(),

                durationMs
        );

        repository.save(event);

        log.info(
                "Proxied {} {} to {} -> {} eventId={} durationMs={}",
                method,
                targetPath,
                targetProperties.serviceId(),
                upstreamResponse.status().value(),
                identity.eventId(),
                durationMs
        );

        if (capturedRequest.truncated()
                || capturedResponse.truncated()) {

            log.debug(
                    "Traffic event {} contains truncated payload evidence; requestTruncated={}, responseTruncated={}",
                    identity.eventId(),
                    capturedRequest.truncated(),
                    capturedResponse.truncated()
            );
        }

        HttpHeaders clientResponseHeaders =
                headerPolicy
                        .getForwardableResponseHeaders(
                                upstreamResponse.headers()
                        );

        return new ForwardedResponse(
                upstreamResponse.status(),
                clientResponseHeaders,
                upstreamResponse.body(),
                upstreamResponse.upstreamReached()
        );
    }

    private String extractTargetPath(
            String requestUri
    ) {
        if (requestUri == null
                || !requestUri.startsWith(
                PROXY_PREFIX
        )) {
            throw new IllegalArgumentException(
                    "Invalid proxy request path"
            );
        }

        String targetPath =
                requestUri.substring(
                        PROXY_PREFIX.length()
                );

        if (targetPath.isBlank()) {
            return "/";
        }

        return targetPath;
    }

    private Map<String, List<String>>
    extractQueryParameters(
            HttpServletRequest request
    ) {
        Map<String, List<String>> result =
                new TreeMap<>();

        request.getParameterMap()
                .forEach(
                        (name, values) ->
                                result.put(
                                        name,
                                        List.of(values)
                                )
                );

        return result;
    }

    private ForwardedResponse
    createBadGatewayResponse() {

        String json =
                """
                {"code":"UPSTREAM_UNAVAILABLE","message":"Resident API is unavailable"}
                """.trim();

        HttpHeaders headers =
                new HttpHeaders();

        headers.setContentType(
                MediaType.APPLICATION_JSON
        );

        return new ForwardedResponse(
                HttpStatus.BAD_GATEWAY,
                headers,
                json.getBytes(
                        StandardCharsets.UTF_8
                ),
                false
        );
    }
}