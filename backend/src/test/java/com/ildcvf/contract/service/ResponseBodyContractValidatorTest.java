package com.ildcvf.contract.service;

import com.ildcvf.contract.model.ContractBaseline;
import com.ildcvf.contract.model.ContractOperation;
import com.ildcvf.contract.model.ResponseBodyValidationResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.traffic.domain.TrafficEvent;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResponseBodyContractValidatorTest {

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

    private static final String VALID_API_ERROR = """
            {
              "timestamp": "2026-09-18T06:00:00Z",
              "status": 400,
              "error": "Bad Request",
              "code": "VALIDATION_FAILED",
              "message": "Request validation failed",
              "path": "/api/residents",
              "fieldErrors": {
                "email": "Email must be valid",
                "firstName": "First name is required"
              }
            }
            """;

    private ResponseBodyContractValidator validator;
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
                new ContractBaselineLoader(baselinePath.toString());

        baseline = loader.getBaseline();

        validator =
                new ResponseBodyContractValidator(
                        new ObjectMapper());
    }

    @Test
    void validResidentResponse() {
        TrafficEvent event =
                event(
                        "e5-001",
                        501,
                        true,
                        "GET",
                        "/api/residents/1",
                        false,
                        200,
                        "application/json",
                        VALID_RESIDENT);

        var result = validate(event);

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void missingRequiredEmail() {
        String body = VALID_RESIDENT.replace(
                "  \"email\": \"anu@example.test\",\n",
                "");

        TrafficEvent event =
                event(
                        "e5-002",
                        502,
                        true,
                        "GET",
                        "/api/residents/1",
                        false,
                        200,
                        "application/json",
                        body);

        var result = validate(event);

        assertEquals(1, result.findings().size());

        DriftFinding finding =
                result.findings().getFirst();

        assertEquals(
                DriftType.MISSING_REQUIRED_RESPONSE_FIELD,
                finding.type());

        assertEquals(
                DriftSeverity.BREAKING,
                finding.severity());

        assertEquals(
                "email",
                finding.fieldPath());

        assertEquals(
                "required",
                finding.expected());

        assertEquals(
                "missing",
                finding.observed());

        assertEquals(
                "e5-002:MISSING_REQUIRED_RESPONSE_FIELD:email",
                finding.findingId());
    }

    @Test
    void residentIdStringInsteadOfInteger() {
        String body = VALID_RESIDENT.replace(
                "\"id\": 1",
                "\"id\": \"1\"");

        TrafficEvent event =
                event(
                        "e5-003",
                        503,
                        true,
                        "GET",
                        "/api/residents/1",
                        false,
                        200,
                        "application/json",
                        body);

        var result = validate(event);

        assertEquals(1, result.findings().size());

        DriftFinding finding =
                result.findings().getFirst();

        assertEquals(
                DriftType.RESPONSE_TYPE_MISMATCH,
                finding.type());

        assertEquals(
                "id",
                finding.fieldPath());

        assertEquals(
                "integer",
                finding.expected());

        assertEquals(
                "string",
                finding.observed());
    }

    @Test
    void residentStatusOutsideEnum() {
        String body = VALID_RESIDENT.replace(
                "\"status\": \"ACTIVE\"",
                "\"status\": \"UNKNOWN\"");

        TrafficEvent event =
                event(
                        "e5-004",
                        504,
                        true,
                        "GET",
                        "/api/residents/1",
                        false,
                        200,
                        "application/json",
                        body);

        var result = validate(event);

        assertEquals(1, result.findings().size());

        DriftFinding finding =
                result.findings().getFirst();

        assertEquals(
                DriftType.RESPONSE_ENUM_MISMATCH,
                finding.type());

        assertEquals(
                "status",
                finding.fieldPath());

        assertEquals(
                "ACTIVE|DISCHARGED|INACTIVE",
                finding.expected());

        assertEquals(
                "UNKNOWN",
                finding.observed());
    }

    @Test
    void realResidentExtraFieldIsAllowed() {
        String body = """
                {
                  "id": 1,
                  "firstName": "Anu",
                  "lastName": "Thomas",
                  "email": "anu@example.test",
                  "dateOfBirth": "1995-05-10",
                  "phoneNumber": null,
                  "status": "ACTIVE",
                  "roomNumber": null,
                  "legacyCode": "X"
                }
                """;

        TrafficEvent event =
                event(
                        "e5-005",
                        505,
                        true,
                        "GET",
                        "/api/residents/1",
                        false,
                        200,
                        "application/json",
                        body);

        var result = validate(event);

        assertTrue(
                result.findings().stream()
                        .noneMatch(finding ->
                                finding.type()
                                        == DriftType.UNEXPECTED_RESPONSE_FIELD));
    }

    @Test
    void syntheticClosedSchemaRejectsExtraField() {
        OpenAPI openApi = new OpenAPI()
                .openapi("3.0.3")
                .info(new Info()
                        .title("Synthetic")
                        .version("1.0"));

        ObjectSchema responseSchema = new ObjectSchema();
        responseSchema.setAdditionalProperties(false);
        responseSchema.addProperties(
                "id",
                new IntegerSchema());

        ApiResponse response =
                new ApiResponse()
                        .description("OK")
                        .content(
                                new Content()
                                        .addMediaType(
                                                "application/json",
                                                new MediaType()
                                                        .schema(responseSchema)));

        Operation operation =
                new Operation()
                        .responses(
                                new ApiResponses()
                                        .addApiResponse(
                                                "200",
                                                response));

        openApi.path(
                "/synthetic",
                new PathItem().get(operation));

        ContractOperation syntheticOperation =
                new ContractOperation(
                        "/synthetic",
                        "GET",
                        "syntheticGet");

        ContractBaseline syntheticBaseline =
                new ContractBaseline(
                        openApi.getOpenapi(),
                        List.of(syntheticOperation),
                        openApi);

        TrafficEvent event =
                event(
                        "e5-006",
                        506,
                        true,
                        "GET",
                        "/synthetic",
                        false,
                        200,
                        "application/json",
                        """
                        {
                          "id": 1,
                          "legacyCode": "X"
                        }
                        """);

        ResponseBodyValidationResult result =
                validator.validate(
                        event,
                        syntheticOperation,
                        syntheticBaseline);

        assertEquals(1, result.findings().size());

        DriftFinding finding =
                result.findings().getFirst();

        assertEquals(
                DriftType.UNEXPECTED_RESPONSE_FIELD,
                finding.type());

        assertEquals(
                DriftSeverity.WARNING,
                finding.severity());

        assertEquals(
                "legacyCode",
                finding.fieldPath());

        assertEquals(
                "legacyCode",
                finding.observed());
    }

    @Test
    void validResidentCollection() {
        TrafficEvent event =
                event(
                        "e5-007",
                        507,
                        true,
                        "GET",
                        "/api/residents",
                        false,
                        200,
                        "application/json",
                        """
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
                        """);

        var result = validate(event);

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void residentCollectionItemEnumMismatch() {
        TrafficEvent event =
                event(
                        "e5-008",
                        508,
                        true,
                        "GET",
                        "/api/residents",
                        false,
                        200,
                        "application/json",
                        """
                        [
                          {
                            "id": 1,
                            "firstName": "Anu",
                            "lastName": "Thomas",
                            "email": "anu@example.test",
                            "dateOfBirth": "1995-05-10",
                            "phoneNumber": null,
                            "status": "UNKNOWN",
                            "roomNumber": null
                          }
                        ]
                        """);

        var result = validate(event);

        assertEquals(1, result.findings().size());

        DriftFinding finding =
                result.findings().getFirst();

        assertEquals(
                DriftType.RESPONSE_ENUM_MISMATCH,
                finding.type());

        assertEquals(
                "[0].status",
                finding.fieldPath());
    }

    @Test
    void collectionRootTypeMismatch() {
        TrafficEvent event =
                event(
                        "e5-009",
                        509,
                        true,
                        "GET",
                        "/api/residents",
                        false,
                        200,
                        "application/json",
                        VALID_RESIDENT);

        var result = validate(event);

        assertEquals(1, result.findings().size());

        DriftFinding finding =
                result.findings().getFirst();

        assertEquals(
                DriftType.RESPONSE_TYPE_MISMATCH,
                finding.type());

        assertEquals(
                "$",
                finding.fieldPath());

        assertTrue(
                finding.findingId().endsWith(
                        ":RESPONSE_TYPE_MISMATCH:$"));
    }

    @Test
    void malformedResponseJson() {
        TrafficEvent event =
                event(
                        "e5-010",
                        510,
                        true,
                        "GET",
                        "/api/residents/1",
                        false,
                        200,
                        "application/json",
                        "{\"id\":");

        var result = validate(event);

        assertTrue(result.findings().isEmpty());
        assertNotNull(result.evaluationMessage());
        assertFalse(result.evaluationMessage().isBlank());
    }

    @Test
    void truncatedResponseBody() {
        TrafficEvent event =
                event(
                        "e5-011",
                        511,
                        true,
                        "GET",
                        "/api/residents/1",
                        true,
                        200,
                        "application/json",
                        "{\"id\":1");

        var result = validate(event);

        assertTrue(result.findings().isEmpty());
        assertNotNull(result.evaluationMessage());
        assertFalse(result.evaluationMessage().isBlank());
    }

    @Test
    void nonJsonRuntimeResponse() {
        TrafficEvent event =
                event(
                        "e5-012",
                        512,
                        true,
                        "GET",
                        "/api/residents/1",
                        false,
                        200,
                        "text/plain",
                        VALID_RESIDENT);

        var result = validate(event);

        assertTrue(result.findings().isEmpty());
        assertNotNull(result.evaluationMessage());
        assertFalse(result.evaluationMessage().isBlank());
    }

    @Test
    void absentExpectedJsonBody() {
        TrafficEvent event =
                event(
                        "e5-013",
                        513,
                        true,
                        "GET",
                        "/api/residents/1",
                        false,
                        200,
                        "application/json",
                        null);

        var result = validate(event);

        assertTrue(result.findings().isEmpty());
        assertNotNull(result.evaluationMessage());
        assertFalse(result.evaluationMessage().isBlank());
    }

    @Test
    void delete204WithoutBodyOrContentType() {
        TrafficEvent event =
                event(
                        "e5-014",
                        514,
                        true,
                        "DELETE",
                        "/api/residents/1",
                        false,
                        204,
                        null,
                        null);

        var result = validate(event);

        assertTrue(result.findings().isEmpty());
        assertNull(result.evaluationMessage());
    }

    @Test
    void validApiError() {
        TrafficEvent event =
                event(
                        "e5-015",
                        515,
                        true,
                        "POST",
                        "/api/residents",
                        false,
                        400,
                        "application/json",
                        VALID_API_ERROR);

        var result = validate(event);

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void apiErrorFieldErrorsWrongTopLevelType() {
        String body = VALID_API_ERROR.replace(
                """
                "fieldErrors": {
                    "email": "Email must be valid",
                    "firstName": "First name is required"
                  }
                """,
                """
                "fieldErrors": "invalid"
                """);

        TrafficEvent event =
                event(
                        "e5-016",
                        516,
                        true,
                        "POST",
                        "/api/residents",
                        false,
                        400,
                        "application/json",
                        body);

        var result = validate(event);

        assertEquals(1, result.findings().size());

        DriftFinding finding =
                result.findings().getFirst();

        assertEquals(
                DriftType.RESPONSE_TYPE_MISMATCH,
                finding.type());

        assertEquals(
                "fieldErrors",
                finding.fieldPath());

        assertEquals(
                "object",
                finding.expected());

        assertEquals(
                "string",
                finding.observed());
    }

    @Test
    void undocumentedResponseStatusSkipsBodyValidation() {
        TrafficEvent event =
                event(
                        "e5-017",
                        517,
                        true,
                        "POST",
                        "/api/residents",
                        false,
                        422,
                        "application/json",
                        """
                        {
                          "completely": "different"
                        }
                        """);

        var result = validate(event);

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void repeatEvaluationIsDeterministic() {
        TrafficEvent event =
                event(
                        "e5-018",
                        518,
                        true,
                        "GET",
                        "/api/residents/1",
                        false,
                        200,
                        "application/json",
                        VALID_RESIDENT.replace(
                                "\"status\": \"ACTIVE\"",
                                "\"status\": \"UNKNOWN\""));

        var first = validate(event);
        var second = validate(event);

        assertEquals(
                first.findings(),
                second.findings());

        assertEquals(
                first.evaluationStatus(),
                second.evaluationStatus());

        assertEquals(
                first.evaluationMessage(),
                second.evaluationMessage());
    }

    private ResponseBodyValidationResult validate(
            TrafficEvent event) {

        ContractOperation operation =
                findOperation(
                        baseline,
                        event.method(),
                        event.requestPath());

        return validator.validate(
                event,
                operation,
                baseline);
    }

    private ContractOperation findOperation(
            ContractBaseline baseline,
            String method,
            String runtimePath) {

        return baseline.operations().stream()
                .filter(operation ->
                        operation.method().equalsIgnoreCase(method))
                .filter(operation ->
                        pathMatches(
                                operation.pathTemplate(),
                                runtimePath))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "No baseline operation for "
                                        + method
                                        + " "
                                        + runtimePath));
    }

    private boolean pathMatches(
            String template,
            String runtimePath) {

        if (template.equals(runtimePath)) {
            return true;
        }

        String regex =
                template.replaceAll(
                        "\\{[^/]+}",
                        "[^/]+");

        return runtimePath.matches(regex);
    }

    private TrafficEvent event(
            String eventId,
            long sequence,
            boolean upstreamReached,
            String method,
            String requestPath,
            boolean responseBodyTruncated,
            int responseStatus,
            String responseContentType,
            String responseBody) {

        return new TrafficEvent(
                eventId,
                sequence,
                Instant.parse(
                        "2026-09-18T06:00:00Z"),
                "resident-api",
                upstreamReached,
                method,
                requestPath,
                null,
                Map.of(),
                Map.of(),
                null,
                null,
                0,
                false,
                responseStatus,
                Map.of(),
                responseContentType,
                responseBody,
                responseBody == null
                        ? 0
                        : responseBody.length(),
                responseBodyTruncated,
                10L);
    }
}
