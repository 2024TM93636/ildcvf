package com.ildcvf.traffic.service;

public record TrafficEventIdentity(
        long sequence,
        String eventId
) {
}