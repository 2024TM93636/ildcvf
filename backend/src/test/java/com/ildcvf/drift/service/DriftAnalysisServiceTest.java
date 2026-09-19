package com.ildcvf.drift.service;

import com.ildcvf.contract.service.*;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.drift.domain.EvaluationStatus;
import com.ildcvf.traffic.domain.TrafficEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DriftAnalysisServiceTest {

    private DriftAnalysisService service;

    @BeforeEach
    void setUp() {
        Path baselinePath = Path.of(
                "..",
                "datasets",
                "openapi",
                "baseline",
                "resident-api-v1.yaml");

        ContractBaselineLoader loader =
                new ContractBaselineLoader(baselinePath.toString());

        ObjectMapper objectMapper = new ObjectMapper();

        service = new DriftAnalysisService(
                loader,
                new ContractOperationMatcher(),
                new RequestContractValidator(objectMapper),
                new ResponseStatusContractValidator(),
                new ResponseBodyContractValidator(objectMapper));
    }

    private static final String VALID_RESIDENT = """
        {
          "id": 1,
          "firstName": "Anu",
          "lastName": "Thomas",
          "email": "anu@example.test",
          "dateOfBirth": "1995-05-10",
          "phoneNumber": null,
          "status": "ACTIVE",
          "roomNumber": null
        }
        """;

    private static final String VALID_RESIDENT_COLLECTION = """
        [
          {
            "id": 1,
            "firstName": "Anu",
            "lastName": "Thomas",
            "email": "anu@example.test",
            "dateOfBirth": "1995-05-10",
            "phoneNumber": null,
            "status": "ACTIVE",
            "roomNumber": null
          }
        ]
        """;

    @Test
    void documentedStaticOperationIsEvaluatedWithoutFindings() {
        TrafficEvent event =
                event("traffic-test-0001", 1, true, "GET", "/api/residents", null, null, false, 200, VALID_RESIDENT_COLLECTION);

        var result = service.analyze(event);

        assertEquals(EvaluationStatus.EVALUATED, result.evaluationStatus());
        assertEquals("/api/residents", result.matchedOpenApiPath());
        assertTrue(result.findings().isEmpty());
        assertNull(result.evaluationMessage());
    }

    @Test
    void documentedTemplatedOperationIsEvaluatedWithoutFindings() {
        TrafficEvent event =
                event("traffic-test-0002", 2, true, "GET", "/api/residents/123", null, null, false, 200);

        var result = service.analyze(event);

        assertEquals(EvaluationStatus.EVALUATED, result.evaluationStatus());
        assertEquals(
                "/api/residents/{id}",
                result.matchedOpenApiPath());
        assertTrue(result.findings().isEmpty());
    }

    @Test
    void undocumentedMethodProducesWarningFinding() {
        TrafficEvent event =
                event("traffic-test-0003", 3, true, "PATCH", "/api/residents", null, null, false, 200);

        var result = service.analyze(event);

        assertEquals(EvaluationStatus.EVALUATED, result.evaluationStatus());
        assertNull(result.matchedOpenApiPath());
        assertEquals(1, result.findings().size());

        var finding = result.findings().getFirst();

        assertEquals(
                DriftType.UNDOCUMENTED_OPERATION,
                finding.type());

        assertEquals(
                DriftSeverity.WARNING,
                finding.severity());

        assertEquals(event.eventId(), finding.trafficEventId());
        assertEquals(event.sequence(), finding.trafficSequence());
        assertEquals("PATCH", finding.method());
        assertEquals("/api/residents", finding.runtimePath());
    }

    @Test
    void undocumentedPathProducesWarningFinding() {
        TrafficEvent event =
                event("traffic-test-0004", 4, true, "GET", "/api/unknown", null, null, false, 200);

        var result = service.analyze(event);

        assertEquals(EvaluationStatus.EVALUATED, result.evaluationStatus());
        assertNull(result.matchedOpenApiPath());
        assertEquals(1, result.findings().size());

        var finding = result.findings().getFirst();

        assertEquals(
                DriftType.UNDOCUMENTED_OPERATION,
                finding.type());

        assertEquals(
                DriftSeverity.WARNING,
                finding.severity());
    }

    @Test
    void upstreamFailureIsNotEvaluatedAndProducesNoDrift() {
        TrafficEvent event =
                event("traffic-test-0005", 5, false, "GET", "/api/residents", null, null, false, 200);

        var result = service.analyze(event);

        assertEquals(
                EvaluationStatus.NOT_EVALUATED,
                result.evaluationStatus());

        assertNull(result.matchedOpenApiPath());
        assertTrue(result.findings().isEmpty());

        assertFalse(
                result.evaluationMessage() == null
                        || result.evaluationMessage().isBlank());
    }

    @Test
    void undocumentedOperationFindingIdIsDeterministic() {
        TrafficEvent event =
                event("traffic-test-0006", 6, true, "PATCH", "/api/residents", null, null, false, 200, VALID_RESIDENT_COLLECTION);

        var first = service.analyze(event);
        var second = service.analyze(event);

        assertEquals(1, first.findings().size());
        assertEquals(1, second.findings().size());

        assertEquals(
                first.findings().getFirst().findingId(),
                second.findings().getFirst().findingId());

        var finding = first.findings().getFirst();

        assertEquals(event.eventId(), finding.trafficEventId());
        assertEquals(event.sequence(), finding.trafficSequence());
        assertEquals(event.method(), finding.method());
        assertEquals(event.requestPath(), finding.runtimePath());
    }

    @Test
    void documentedPostWithValidBodyIsEvaluatedWithoutFindings() {
        TrafficEvent event = event(
                "traffic-test-c-001",
                101,
                true,
                "POST",
                "/api/residents",
                "application/json",
                """
                {
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE"
                }
                """,
                false, 201);

        var result = service.analyze(event);

        assertEquals(
                EvaluationStatus.EVALUATED,
                result.evaluationStatus());

        assertEquals(
                "/api/residents",
                result.matchedOpenApiPath());

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void documentedPostProducesRequestSchemaFinding() {
        TrafficEvent event = event(
                "traffic-test-c-002",
                102,
                true,
                "POST",
                "/api/residents",
                "application/json",
                """
                {
                  "firstName": 123,
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE"
                }
                """,
                false, 201);

        var result = service.analyze(event);

        assertEquals(
                EvaluationStatus.EVALUATED,
                result.evaluationStatus());

        assertEquals(1, result.findings().size());

        assertEquals(
                DriftType.REQUEST_TYPE_MISMATCH,
                result.findings().getFirst().type());
    }

    @Test
    void documentedPostWithTruncatedBodyIsPartiallyEvaluated() {
        TrafficEvent event = event(
                "traffic-test-c-003",
                103,
                true,
                "POST",
                "/api/residents",
                "application/json",
                "{\"firstName\":\"An",
                true, 201);

        var result = service.analyze(event);

        assertEquals(
                EvaluationStatus.PARTIALLY_EVALUATED,
                result.evaluationStatus());

        assertTrue(result.findings().isEmpty());
        assertNotNull(result.evaluationMessage());
    }

    @Test
    void validRequestWithUndocumentedResponseProducesResponseFinding() {
        TrafficEvent event = event(
                "traffic-d-001",
                201,
                true,
                "POST",
                "/api/residents",
                "application/json",
                """
                {
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE"
                }
                """,
                false,
                422);

        var result = service.analyze(event);

        assertEquals(
                EvaluationStatus.EVALUATED,
                result.evaluationStatus());

        assertEquals(1, result.findings().size());

        var finding = result.findings().getFirst();

        assertEquals(
                DriftType.UNDOCUMENTED_RESPONSE_STATUS,
                finding.type());

        assertEquals(
                DriftSeverity.WARNING,
                finding.severity());

        assertEquals("422", finding.observed());
    }

    @Test
    void invalidRequestWithDocumentedResponseProducesRequestFindingOnly() {
        TrafficEvent event = event(
                "traffic-d-002",
                202,
                true,
                "POST",
                "/api/residents",
                "application/json",
                """
                {
                  "firstName": 123,
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE"
                }
                """,
                false,
                201);

        var result = service.analyze(event);

        assertEquals(
                EvaluationStatus.EVALUATED,
                result.evaluationStatus());

        assertEquals(1, result.findings().size());

        assertEquals(
                DriftType.REQUEST_TYPE_MISMATCH,
                result.findings().getFirst().type());
    }

    @Test
    void combinedRequestAndResponseDriftProducesBothFindings() {
        TrafficEvent event = event(
                "traffic-d-003",
                203,
                true,
                "POST",
                "/api/residents",
                "application/json",
                """
                {
                  "firstName": 123,
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE"
                }
                """,
                false,
                422);

        var result = service.analyze(event);

        assertEquals(
                EvaluationStatus.EVALUATED,
                result.evaluationStatus());

        assertEquals(2, result.findings().size());

        assertTrue(
                result.findings().stream()
                        .anyMatch(finding ->
                                finding.type()
                                        == DriftType.REQUEST_TYPE_MISMATCH));

        assertTrue(
                result.findings().stream()
                        .anyMatch(finding ->
                                finding.type()
                                        == DriftType.UNDOCUMENTED_RESPONSE_STATUS));

        assertEquals(
                result.findings().stream()
                        .sorted(
                                Comparator
                                        .comparing(
                                                (DriftFinding f) ->
                                                        f.type().name())
                                        .thenComparing(
                                                f -> f.fieldPath() == null
                                                        ? ""
                                                        : f.fieldPath())
                                        .thenComparing(
                                                DriftFinding::findingId))
                        .map(DriftFinding::findingId)
                        .toList(),
                result.findings().stream()
                        .map(DriftFinding::findingId)
                        .toList());
    }

    @Test
    void partialRequestEvaluationRetainsIndependentResponseDrift() {
        TrafficEvent event = event(
                "traffic-d-004",
                204,
                true,
                "POST",
                "/api/residents",
                "application/json",
                "{\"firstName\":\"An",
                true,
                422);

        var result = service.analyze(event);

        assertEquals(
                EvaluationStatus.PARTIALLY_EVALUATED,
                result.evaluationStatus());

        assertTrue(
                result.findings().stream()
                        .noneMatch(finding ->
                                finding.type().name().startsWith("REQUEST_")));

        assertEquals(
                1,
                result.findings().stream()
                        .filter(finding ->
                                finding.type()
                                        == DriftType.UNDOCUMENTED_RESPONSE_STATUS)
                        .count());

        assertNotNull(result.evaluationMessage());
        assertFalse(result.evaluationMessage().isBlank());
    }

    @Test
    void upstreamFailureWithSynthetic502IsNotEvaluated() {
        TrafficEvent event = event(
                "traffic-d-005",
                205,
                false,
                "POST",
                "/api/residents",
                "application/json",
                """
                {
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE"
                }
                """,
                false,
                502);

        var result = service.analyze(event);

        assertEquals(
                EvaluationStatus.NOT_EVALUATED,
                result.evaluationStatus());

        assertTrue(result.findings().isEmpty());

        assertNotNull(result.evaluationMessage());
        assertFalse(result.evaluationMessage().isBlank());
    }

    @Test
    void getWithoutRequestSchemaWithDocumentedResponseIsEvaluated() {
        TrafficEvent event =
                event(
                        "traffic-d-006",
                        206,
                        true,
                        "GET",
                        "/api/residents",
                        null,
                        null,
                        false,
                        200,
                        VALID_RESIDENT_COLLECTION);

        var result = service.analyze(event);

        assertEquals(
                EvaluationStatus.EVALUATED,
                result.evaluationStatus());

        assertEquals(
                "/api/residents",
                result.matchedOpenApiPath());

        assertTrue(result.findings().isEmpty());
    }

    private TrafficEvent event(
            String eventId,
            long sequence,
            boolean upstreamReached,
            String method,
            String requestPath,
            String requestContentType,
            String requestBody,
            boolean requestBodyTruncated,
            int responseStatus) {

        return event(
                eventId,
                sequence,
                upstreamReached,
                method,
                requestPath,
                requestContentType,
                requestBody,
                requestBodyTruncated,
                responseStatus,
                VALID_RESIDENT);
    }

    private TrafficEvent event(
            String eventId,
            long sequence,
            boolean upstreamReached,
            String method,
            String requestPath,
            String requestContentType,
            String requestBody,
            boolean requestBodyTruncated,
            int responseStatus,
            String responseBody) {

        return new TrafficEvent(
                eventId,
                sequence,
                Instant.parse("2026-09-18T06:00:00Z"),
                "resident-api",
                upstreamReached,
                method,
                requestPath,
                null,
                Map.of(),
                Map.of(),
                requestContentType,
                requestBody,
                requestBody == null ? 0 : requestBody.length(),
                requestBodyTruncated,
                responseStatus,
                Map.of(),
                "application/json",
                responseBody,
                responseBody == null ? 0 : responseBody.length(),
                false,
                10L);
    }
}