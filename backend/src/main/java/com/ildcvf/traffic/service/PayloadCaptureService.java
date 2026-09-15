package com.ildcvf.traffic.service;

import com.ildcvf.traffic.config.TrafficCaptureProperties;
import com.ildcvf.traffic.domain.CapturedPayload;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Service
public class PayloadCaptureService {

    private final TrafficCaptureProperties properties;

    public PayloadCaptureService(
            TrafficCaptureProperties properties
    ) {
        this.properties = properties;
    }

    public CapturedPayload capture(
            byte[] payload,
            String contentType
    ) {
        if (payload == null || payload.length == 0) {
            return CapturedPayload.empty();
        }

        if (!isJson(contentType)) {
            return new CapturedPayload(
                    null,
                    payload.length,
                    false
            );
        }

        int maximum = properties.maxCaptureBytes();

        if (maximum <= 0) {
            return new CapturedPayload(
                    null,
                    payload.length,
                    true
            );
        }

        if (payload.length <= maximum) {
            return new CapturedPayload(
                    new String(
                            payload,
                            StandardCharsets.UTF_8
                    ),
                    payload.length,
                    false
            );
        }

        byte[] truncatedBytes =
                Arrays.copyOf(payload, maximum);

        return new CapturedPayload(
                new String(
                        truncatedBytes,
                        StandardCharsets.UTF_8
                ),
                payload.length,
                true
        );
    }

    private boolean isJson(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return false;
        }

        try {
            MediaType mediaType =
                    MediaType.parseMediaType(contentType);

            return MediaType.APPLICATION_JSON.includes(mediaType)
                    || mediaType.getSubtype()
                    .toLowerCase()
                    .endsWith("+json");

        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}