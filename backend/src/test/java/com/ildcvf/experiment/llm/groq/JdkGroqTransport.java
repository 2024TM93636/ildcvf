package com.ildcvf.experiment.llm.groq;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

public final class JdkGroqTransport implements GroqTransport {

    private final HttpClient httpClient;

    public JdkGroqTransport() {
        this(
                HttpClient.newBuilder()
                        .followRedirects(
                                HttpClient.Redirect.NEVER)
                        .build());
    }

    JdkGroqTransport(HttpClient httpClient) {
        this.httpClient =
                Objects.requireNonNull(
                        httpClient,
                        "httpClient must not be null");
    }

    @Override
    public GroqHttpResponse post(
            URI endpoint,
            String authorizationSecret,
            String requestBody,
            Duration timeout
    ) throws GroqTransportException {

        Objects.requireNonNull(
                endpoint,
                "endpoint must not be null");

        if (authorizationSecret == null
                || authorizationSecret.isBlank()) {
            throw new IllegalArgumentException(
                    "authorizationSecret must not be blank");
        }

        Objects.requireNonNull(
                requestBody,
                "requestBody must not be null");

        Objects.requireNonNull(
                timeout,
                "timeout must not be null");

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(endpoint)
                        .timeout(timeout)
                        .header(
                                "Content-Type",
                                "application/json")
                        .header(
                                "Authorization",
                                "Bearer "
                                        + authorizationSecret)
                        .POST(
                                HttpRequest.BodyPublishers
                                        .ofString(
                                                requestBody,
                                                StandardCharsets.UTF_8))
                        .build();

        long start =
                System.nanoTime();

        try {
            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers
                                    .ofString(
                                            StandardCharsets.UTF_8));

            Duration latency =
                    Duration.ofNanos(
                            System.nanoTime() - start);

            return new GroqHttpResponse(
                    response.statusCode(),
                    response.body(),
                    response.headers().map(),
                    latency
            );

        } catch (HttpTimeoutException exception) {
            throw new GroqTransportException(
                    GroqTransportException.Kind.TIMEOUT,
                    "Groq HTTP request timed out.",
                    exception);

        } catch (IOException exception) {
            throw new GroqTransportException(
                    GroqTransportException.Kind.TRANSPORT_FAILURE,
                    "Groq HTTP transport failed.",
                    exception);

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new GroqTransportException(
                    GroqTransportException.Kind.TRANSPORT_FAILURE,
                    "Groq HTTP request was interrupted.",
                    exception);
        }
    }
}