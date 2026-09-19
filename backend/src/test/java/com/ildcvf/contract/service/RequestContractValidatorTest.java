package com.ildcvf.contract.service;

import com.ildcvf.contract.model.ContractBaseline;
import com.ildcvf.contract.model.ContractOperation;
import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.drift.domain.EvaluationStatus;
import com.ildcvf.traffic.domain.TrafficEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RequestContractValidatorTest {

    private RequestContractValidator validator;
    private ContractBaseline baseline;

    @BeforeEach
    void setUp() {
        Path baselinePath = Path.of(
                "..",
                "datasets",
                "openapi",
                "baseline",
                "resident-api-v1.yaml");

        ContractBaselineLoader loader =
                new ContractBaselineLoader(
                        baselinePath.toString());

        baseline = loader.getBaseline();

        validator =
                new RequestContractValidator(
                        new ObjectMapper());
    }

    private ContractOperation postOperation() {
        return baseline.operations().stream()
                .filter(operation ->
                        operation.method().equals("POST")
                                && operation.pathTemplate()
                                .equals("/api/residents"))
                .findFirst()
                .orElseThrow();
    }

    private ContractOperation putOperation() {
        return baseline.operations().stream()
                .filter(operation ->
                        operation.method().equals("PUT")
                                && operation.pathTemplate()
                                .equals("/api/residents/{id}"))
                .findFirst()
                .orElseThrow();
    }

    private String validBody() {
        return """
                {
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE",
                  "phoneNumber": "9999999999",
                  "roomNumber": "A101"
                }
                """;
    }

    private TrafficEvent event(
            String eventId,
            long sequence,
            String method,
            String path,
            String contentType,
            String body,
            boolean truncated) {

        return new TrafficEvent(
                eventId,
                sequence,
                Instant.parse("2026-09-18T06:00:00Z"),
                "resident-api",
                true,
                method,
                path,
                null,
                Map.of(),
                Map.of(),
                contentType,
                body,
                body == null ? 0 : body.length(),
                truncated,
                200,
                Map.of(),
                "application/json",
                "{}",
                2,
                false,
                10L);
    }

    @Test
    void validPostProducesNoRequestFindings() {
        var result = validator.validate(
                event(
                        "traffic-c-001",
                        1,
                        "POST",
                        "/api/residents",
                        "application/json",
                        validBody(),
                        false),
                postOperation(),
                baseline);

        assertEquals(
                EvaluationStatus.EVALUATED,
                result.evaluationStatus());

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void validPutResolvesUpdateResidentRequest() {
        var result = validator.validate(
                event(
                        "traffic-c-002",
                        2,
                        "PUT",
                        "/api/residents/1",
                        "application/json",
                        validBody(),
                        false),
                putOperation(),
                baseline);

        assertEquals(
                EvaluationStatus.EVALUATED,
                result.evaluationStatus());

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void unexpectedFieldProducesWarning() {
        String body = """
                {
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE",
                  "debug": true
                }
                """;

        var result = validator.validate(
                event(
                        "traffic-c-003",
                        3,
                        "POST",
                        "/api/residents",
                        "application/json",
                        body,
                        false),
                postOperation(),
                baseline);

        assertEquals(1, result.findings().size());

        var finding = result.findings().getFirst();

        assertEquals(
                DriftType.UNEXPECTED_REQUEST_FIELD,
                finding.type());

        assertEquals(
                DriftSeverity.WARNING,
                finding.severity());

        assertEquals("/debug", finding.fieldPath());

        assertEquals(
                "traffic-c-003:UNEXPECTED_REQUEST_FIELD:/debug",
                finding.findingId());
    }

    @Test
    void missingRequiredFieldProducesBreakingFinding() {
        String body = """
                {
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE"
                }
                """;

        var result = validator.validate(
                event(
                        "traffic-c-004",
                        4,
                        "POST",
                        "/api/residents",
                        "application/json",
                        body,
                        false),
                postOperation(),
                baseline);

        assertEquals(1, result.findings().size());

        var finding = result.findings().getFirst();

        assertEquals(
                DriftType.MISSING_REQUIRED_REQUEST_FIELD,
                finding.type());

        assertEquals(
                DriftSeverity.BREAKING,
                finding.severity());

        assertEquals("/email", finding.fieldPath());
        assertEquals("required property", finding.expected());
        assertEquals("absent", finding.observed());
    }

    @Test
    void absentOptionalFieldsProduceNoFindings() {
        String body = """
                {
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE"
                }
                """;

        var result = validator.validate(
                event(
                        "traffic-c-005",
                        5,
                        "POST",
                        "/api/residents",
                        "application/json",
                        body,
                        false),
                postOperation(),
                baseline);

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void nullableOptionalFieldsAcceptJsonNull() {
        String body = """
                {
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE",
                  "phoneNumber": null,
                  "roomNumber": null
                }
                """;

        var result = validator.validate(
                event(
                        "traffic-c-006",
                        6,
                        "POST",
                        "/api/residents",
                        "application/json",
                        body,
                        false),
                postOperation(),
                baseline);

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void wrongJsonTypeProducesBreakingFinding() {
        String body = """
                {
                  "firstName": 123,
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE"
                }
                """;

        var result = validator.validate(
                event(
                        "traffic-c-007",
                        7,
                        "POST",
                        "/api/residents",
                        "application/json",
                        body,
                        false),
                postOperation(),
                baseline);

        assertEquals(1, result.findings().size());

        var finding = result.findings().getFirst();

        assertEquals(
                DriftType.REQUEST_TYPE_MISMATCH,
                finding.type());

        assertEquals(
                DriftSeverity.BREAKING,
                finding.severity());

        assertEquals("/firstName", finding.fieldPath());
        assertEquals("string", finding.expected());
        assertEquals("integer", finding.observed());
    }

    @Test
    void invalidResidentStatusProducesEnumMismatch() {
        String body = """
                {
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "UNKNOWN"
                }
                """;

        var result = validator.validate(
                event(
                        "traffic-c-008",
                        8,
                        "POST",
                        "/api/residents",
                        "application/json",
                        body,
                        false),
                postOperation(),
                baseline);

        assertEquals(1, result.findings().size());

        var finding = result.findings().getFirst();

        assertEquals(
                DriftType.REQUEST_ENUM_MISMATCH,
                finding.type());

        assertEquals(
                DriftSeverity.BREAKING,
                finding.severity());

        assertEquals("/status", finding.fieldPath());
        assertEquals(
                "ACTIVE|DISCHARGED|INACTIVE",
                finding.expected());

        assertEquals("UNKNOWN", finding.observed());
    }

    @Test
    void wrongTypeEnumProducesOnlyTypeMismatch() {
        String body = """
                {
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": 123
                }
                """;

        var result = validator.validate(
                event(
                        "traffic-c-009",
                        9,
                        "POST",
                        "/api/residents",
                        "application/json",
                        body,
                        false),
                postOperation(),
                baseline);

        assertEquals(1, result.findings().size());

        assertEquals(
                DriftType.REQUEST_TYPE_MISMATCH,
                result.findings().getFirst().type());

        assertEquals(
                "/status",
                result.findings().getFirst().fieldPath());
    }

    @Test
    void multipleFindingsHaveDeterministicOrderAndIds() {
        String body = """
                {
                  "firstName": 123,
                  "lastName": "Thomas",
                  "dateOfBirth": "1995-05-10",
                  "status": "UNKNOWN",
                  "debug": true
                }
                """;

        TrafficEvent event = event(
                "traffic-c-010",
                10,
                "POST",
                "/api/residents",
                "application/json",
                body,
                false);

        var first =
                validator.validate(
                        event,
                        postOperation(),
                        baseline);

        var second =
                validator.validate(
                        event,
                        postOperation(),
                        baseline);

        assertEquals(4, first.findings().size());

        List<DriftType> expectedTypes = List.of(
                DriftType.MISSING_REQUIRED_REQUEST_FIELD,
                DriftType.REQUEST_ENUM_MISMATCH,
                DriftType.REQUEST_TYPE_MISMATCH,
                DriftType.UNEXPECTED_REQUEST_FIELD);

        assertEquals(
                expectedTypes,
                first.findings().stream()
                        .map(finding -> finding.type())
                        .toList());

        List<String> firstIds =
                first.findings().stream()
                        .map(finding -> finding.findingId())
                        .toList();

        List<String> secondIds =
                second.findings().stream()
                        .map(finding -> finding.findingId())
                        .toList();

        assertEquals(firstIds, secondIds);

        assertEquals(
                List.of(
                        "traffic-c-010:MISSING_REQUIRED_REQUEST_FIELD:/email",
                        "traffic-c-010:REQUEST_ENUM_MISMATCH:/status",
                        "traffic-c-010:REQUEST_TYPE_MISMATCH:/firstName",
                        "traffic-c-010:UNEXPECTED_REQUEST_FIELD:/debug"),
                firstIds);
    }

    @Test
    void truncatedBodyIsPartiallyEvaluatedWithoutFindings() {
        var result = validator.validate(
                event(
                        "traffic-c-011",
                        11,
                        "POST",
                        "/api/residents",
                        "application/json",
                        "{\"firstName\":\"An",
                        true),
                postOperation(),
                baseline);

        assertEquals(
                EvaluationStatus.PARTIALLY_EVALUATED,
                result.evaluationStatus());

        assertTrue(result.findings().isEmpty());
        assertNotNull(result.evaluationMessage());
        assertFalse(result.evaluationMessage().isBlank());
    }

    @Test
    void malformedJsonIsPartiallyEvaluatedWithoutFindings() {
        var result = validator.validate(
                event(
                        "traffic-c-012",
                        12,
                        "POST",
                        "/api/residents",
                        "application/json",
                        "{\"firstName\":",
                        false),
                postOperation(),
                baseline);

        assertEquals(
                EvaluationStatus.PARTIALLY_EVALUATED,
                result.evaluationStatus());

        assertTrue(result.findings().isEmpty());
        assertFalse(result.evaluationMessage().isBlank());
    }

    @Test
    void nonObjectJsonRootIsPartiallyEvaluated() {
        var result = validator.validate(
                event(
                        "traffic-c-013",
                        13,
                        "POST",
                        "/api/residents",
                        "application/json",
                        "[]",
                        false),
                postOperation(),
                baseline);

        assertEquals(
                EvaluationStatus.PARTIALLY_EVALUATED,
                result.evaluationStatus());

        assertTrue(result.findings().isEmpty());
        assertFalse(result.evaluationMessage().isBlank());
    }

    @Test
    void nonJsonContentTypeDoesNotCreateSchemaFindings() {
        var result = validator.validate(
                event(
                        "traffic-c-014",
                        14,
                        "POST",
                        "/api/residents",
                        "text/plain",
                        validBody(),
                        false),
                postOperation(),
                baseline);

        assertEquals(
                EvaluationStatus.EVALUATED,
                result.evaluationStatus());

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void jsonContentTypeWithCharsetIsValidated() {
        String body = """
                {
                  "firstName": 123,
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "status": "ACTIVE"
                }
                """;

        var result = validator.validate(
                event(
                        "traffic-c-015",
                        15,
                        "POST",
                        "/api/residents",
                        "Application/JSON; charset=UTF-8",
                        body,
                        false),
                postOperation(),
                baseline);

        assertEquals(1, result.findings().size());

        assertEquals(
                DriftType.REQUEST_TYPE_MISMATCH,
                result.findings().getFirst().type());
    }
}