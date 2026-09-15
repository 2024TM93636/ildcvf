package com.ildcvf.traffic.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ildcvf.targets.resident")
public record ResidentTargetProperties(
        String serviceId,
        String baseUrl
) {
}