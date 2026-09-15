package com.ildcvf.traffic.client;

import com.ildcvf.traffic.config.ResidentTargetProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

@Component
public class ResidentForwardingClient {

    private final RestClient restClient;
    private final ResidentTargetProperties properties;

    public ResidentForwardingClient(
            RestClient.Builder restClientBuilder,
            ResidentTargetProperties properties
    ) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
    }

    public ForwardedResponse forward(
            HttpMethod method,
            String path,
            String queryString,
            HttpHeaders requestHeaders,
            byte[] requestBody
    ) {
        URI targetUri = buildTargetUri(
                path,
                queryString
        );

        var requestSpec = restClient
                .method(method)
                .uri(targetUri)
                .headers(headers ->
                        headers.putAll(requestHeaders)
                );

        if (requestBody != null
                && requestBody.length > 0) {
            requestSpec.body(requestBody);
        }

        return requestSpec.exchange(
                (request, response) -> {

                    HttpHeaders responseHeaders =
                            new HttpHeaders();

                    responseHeaders.putAll(
                            response.getHeaders()
                    );

                    byte[] responseBody =
                            response.getBody()
                                    .readAllBytes();

                    return new ForwardedResponse(
                            response.getStatusCode(),
                            responseHeaders,
                            responseBody,
                            true
                    );
                }
        );
    }

    private URI buildTargetUri(
            String path,
            String queryString
    ) {
        String baseUrl = properties.baseUrl();

        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(
                    0,
                    baseUrl.length() - 1
            );
        }

        String uri = baseUrl + path;

        if (queryString != null
                && !queryString.isBlank()) {
            uri += "?" + queryString;
        }

        return URI.create(uri);
    }
}