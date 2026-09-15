package com.ildcvf.traffic.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;

public record ForwardedResponse(
        HttpStatusCode status,
        HttpHeaders headers,
        byte[] body,
        boolean upstreamReached
) {

    public ForwardedResponse {
        body = body == null ? new byte[0] : body.clone();

        HttpHeaders headerCopy = new HttpHeaders();
        if (headers != null) {
            headerCopy.putAll(headers);
        }
        headers = headerCopy;
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}