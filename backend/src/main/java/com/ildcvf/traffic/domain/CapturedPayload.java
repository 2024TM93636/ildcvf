package com.ildcvf.traffic.domain;

public record CapturedPayload(
        String body,
        int sizeBytes,
        boolean truncated
) {

    public static CapturedPayload empty() {
        return new CapturedPayload(null, 0, false);
    }
}