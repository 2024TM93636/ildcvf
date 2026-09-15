package com.ildcvf.traffic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "ildcvf.traffic")
public record TrafficCaptureProperties(
        int maxCaptureBytes,
        List<String> requestHeaderAllowlist,
        List<String> responseHeaderAllowlist
) {
}