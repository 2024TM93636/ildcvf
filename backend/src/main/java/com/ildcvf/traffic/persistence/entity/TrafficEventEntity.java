package com.ildcvf.traffic.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "traffic_events")
public class TrafficEventEntity {

    @Id
    @Column(name = "sequence", nullable = false)
    private long sequence;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "target_service", nullable = false)
    private String targetService;

    @Column(name = "upstream_reached", nullable = false)
    private boolean upstreamReached;

    @Column(name = "method", nullable = false)
    private String method;

    @Column(name = "request_path", nullable = false)
    private String requestPath;

    @Column(name = "query_string")
    private String queryString;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_parameters", nullable = false, columnDefinition = "jsonb")
    private Map<String, List<String>> queryParameters;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_headers", nullable = false, columnDefinition = "jsonb")
    private Map<String, List<String>> requestHeaders;

    @Column(name = "request_content_type")
    private String requestContentType;

    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    @Column(name = "request_body_size_bytes", nullable = false)
    private long requestBodySizeBytes;

    @Column(name = "request_body_truncated", nullable = false)
    private boolean requestBodyTruncated;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_headers", nullable = false, columnDefinition = "jsonb")
    private Map<String, List<String>> responseHeaders;

    @Column(name = "response_content_type")
    private String responseContentType;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Column(name = "response_body_size_bytes", nullable = false)
    private long responseBodySizeBytes;

    @Column(name = "response_body_truncated", nullable = false)
    private boolean responseBodyTruncated;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    protected TrafficEventEntity() {
        // Required by JPA/Hibernate.
    }

    public TrafficEventEntity(
            long sequence,
            String eventId,
            Instant timestamp,
            String targetService,
            boolean upstreamReached,
            String method,
            String requestPath,
            String queryString,
            Map<String, List<String>> queryParameters,
            Map<String, List<String>> requestHeaders,
            String requestContentType,
            String requestBody,
            long requestBodySizeBytes,
            boolean requestBodyTruncated,
            int responseStatus,
            Map<String, List<String>> responseHeaders,
            String responseContentType,
            String responseBody,
            long responseBodySizeBytes,
            boolean responseBodyTruncated,
            long durationMs
    ) {
        this.sequence = sequence;
        this.eventId = eventId;
        this.timestamp = timestamp;
        this.targetService = targetService;
        this.upstreamReached = upstreamReached;
        this.method = method;
        this.requestPath = requestPath;
        this.queryString = queryString;
        this.queryParameters = queryParameters;
        this.requestHeaders = requestHeaders;
        this.requestContentType = requestContentType;
        this.requestBody = requestBody;
        this.requestBodySizeBytes = requestBodySizeBytes;
        this.requestBodyTruncated = requestBodyTruncated;
        this.responseStatus = responseStatus;
        this.responseHeaders = responseHeaders;
        this.responseContentType = responseContentType;
        this.responseBody = responseBody;
        this.responseBodySizeBytes = responseBodySizeBytes;
        this.responseBodyTruncated = responseBodyTruncated;
        this.durationMs = durationMs;
    }

    public long getSequence() {
        return sequence;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getTargetService() {
        return targetService;
    }

    public boolean isUpstreamReached() {
        return upstreamReached;
    }

    public String getMethod() {
        return method;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getQueryString() {
        return queryString;
    }

    public Map<String, List<String>> getQueryParameters() {
        return queryParameters;
    }

    public Map<String, List<String>> getRequestHeaders() {
        return requestHeaders;
    }

    public String getRequestContentType() {
        return requestContentType;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public long getRequestBodySizeBytes() {
        return requestBodySizeBytes;
    }

    public boolean isRequestBodyTruncated() {
        return requestBodyTruncated;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders;
    }

    public String getResponseContentType() {
        return responseContentType;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public long getResponseBodySizeBytes() {
        return responseBodySizeBytes;
    }

    public boolean isResponseBodyTruncated() {
        return responseBodyTruncated;
    }

    public long getDurationMs() {
        return durationMs;
    }
}