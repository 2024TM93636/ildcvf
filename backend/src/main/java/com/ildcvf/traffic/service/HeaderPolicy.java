package com.ildcvf.traffic.service;

import com.ildcvf.traffic.config.TrafficCaptureProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class HeaderPolicy {

    private static final Set<String> NON_FORWARDABLE_HEADERS = Set.of(
            "host",
            "content-length",
            "connection",
            "keep-alive",
            "transfer-encoding",
            "te",
            "trailer",
            "upgrade",
            "proxy-authenticate",
            "proxy-authorization"
    );

    private static final Set<String> EXPLICIT_SENSITIVE_HEADERS = Set.of(
            "authorization",
            "proxy-authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "api-key",
            "x-auth-token",
            "x-access-token"
    );

    private final TrafficCaptureProperties properties;

    public HeaderPolicy(TrafficCaptureProperties properties) {
        this.properties = properties;
    }

    public HttpHeaders getForwardableRequestHeaders(
            HttpServletRequest request
    ) {
        HttpHeaders result = new HttpHeaders();

        Enumeration<String> names = request.getHeaderNames();

        if (names == null) {
            return result;
        }

        while (names.hasMoreElements()) {
            String name = names.nextElement();

            if (isNonForwardable(name)) {
                continue;
            }

            Enumeration<String> values = request.getHeaders(name);

            while (values.hasMoreElements()) {
                result.add(name, values.nextElement());
            }
        }

        return result;
    }

    public HttpHeaders getForwardableResponseHeaders(
            HttpHeaders upstreamHeaders
    ) {
        HttpHeaders result = new HttpHeaders();

        upstreamHeaders.forEach((name, values) -> {
            if (!isNonForwardable(name)) {
                result.put(name, new ArrayList<>(values));
            }
        });

        return result;
    }

    public Map<String, List<String>> captureRequestHeaders(
            HttpServletRequest request
    ) {
        Map<String, List<String>> result = new LinkedHashMap<>();

        for (String configuredName :
                properties.requestHeaderAllowlist()) {

            if (isSensitive(configuredName)) {
                continue;
            }

            Enumeration<String> values =
                    request.getHeaders(configuredName);

            if (values != null && values.hasMoreElements()) {
                result.put(
                        configuredName,
                        Collections.list(values)
                );
            }
        }

        return result;
    }

    public Map<String, List<String>> captureResponseHeaders(
            HttpHeaders headers
    ) {
        Map<String, List<String>> result = new LinkedHashMap<>();

        for (String configuredName :
                properties.responseHeaderAllowlist()) {

            if (isSensitive(configuredName)) {
                continue;
            }

            List<String> values = headers.get(configuredName);

            if (values != null && !values.isEmpty()) {
                result.put(
                        configuredName,
                        List.copyOf(values)
                );
            }
        }

        return result;
    }

    boolean isSensitive(String headerName) {
        if (headerName == null) {
            return false;
        }

        String normalized =
                headerName.toLowerCase(Locale.ROOT);

        if (EXPLICIT_SENSITIVE_HEADERS.contains(normalized)) {
            return true;
        }

        return normalized.contains("api-key")
                || normalized.contains("apikey")
                || normalized.contains("auth-token")
                || normalized.contains("access-token")
                || normalized.contains("secret");
    }

    private boolean isNonForwardable(String headerName) {
        return headerName != null
                && NON_FORWARDABLE_HEADERS.contains(
                headerName.toLowerCase(Locale.ROOT)
        );
    }
}