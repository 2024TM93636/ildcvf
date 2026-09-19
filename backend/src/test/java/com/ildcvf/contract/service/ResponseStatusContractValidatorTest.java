package com.ildcvf.contract.service;

import com.ildcvf.contract.model.ContractBaseline;
import com.ildcvf.contract.model.ContractOperation;
import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.drift.domain.EvaluationStatus;
import com.ildcvf.traffic.domain.TrafficEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResponseStatusContractValidatorTest {

    private ResponseStatusContractValidator validator;
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
        validator = new ResponseStatusContractValidator();
    }

    private ContractOperation operation(
            String method,
            String pathTemplate) {

        return baseline.operations().stream()
                .filter(operation ->
                        operation.method().equals(method)
                                && operation.pathTemplate()
                                .equals(pathTemplate))
                .findFirst()
                .orElseThrow();
    }

    private TrafficEvent event(
            String eventId,
            long sequence,
            String method,
            String path,
            int responseStatus) {

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
                null,
                null,
                0,
                false,
                responseStatus,
                Map.of(),
                "application/json",
                "{}",
                2,
                false,
                10L);
    }

    @Test
    void documentedSuccessStatusProducesNoFinding() {
        var result = validator.validate(
                event(
                        "traffic-d-001",
                        1,
                        "POST",
                        "/api/residents",
                        201),
                operation("POST", "/api/residents"),
                baseline);

        assertEquals(
                EvaluationStatus.EVALUATED,
                result.evaluationStatus());

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void anotherDocumentedStatusProducesNoFinding() {
        var result = validator.validate(
                event(
                        "traffic-d-002",
                        2,
                        "POST",
                        "/api/residents",
                        400),
                operation("POST", "/api/residents"),
                baseline);

        assertTrue(result.findings().isEmpty());
    }

    @Test
    void undocumentedStatusProducesWarning() {
        var result = validator.validate(
                event(
                        "traffic-d-003",
                        3,
                        "POST",
                        "/api/residents",
                        422),
                operation("POST", "/api/residents"),
                baseline);

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

        assertNull(finding.fieldPath());

        assertEquals("422", finding.observed());

        assertEquals(
                "traffic-d-003:UNDOCUMENTED_RESPONSE_STATUS:422",
                finding.findingId());
    }

    @Test
    void repeatedEvaluationProducesSameFindingId() {
        TrafficEvent event =
                event(
                        "traffic-d-004",
                        4,
                        "POST",
                        "/api/residents",
                        422);

        ContractOperation operation =
                operation("POST", "/api/residents");

        var first =
                validator.validate(event, operation, baseline);

        var second =
                validator.validate(event, operation, baseline);

        assertEquals(
                first.findings().stream()
                        .map(finding -> finding.findingId())
                        .toList(),
                second.findings().stream()
                        .map(finding -> finding.findingId())
                        .toList());
    }

    @Test
    void defaultResponseAcceptsOtherwiseUnlistedStatus() {
        ApiResponses responses =
                new ApiResponses()
                        .addApiResponse(
                                "200",
                                new ApiResponse()
                                        .description("OK"))
                        .addApiResponse(
                                "default",
                                new ApiResponse()
                                        .description("Default response"));

        Operation syntheticOperation =
                new Operation()
                        .operationId("synthetic")
                        .responses(responses);

        PathItem syntheticPath =
                new PathItem()
                        .get(syntheticOperation);

        OpenAPI syntheticOpenApi =
                new OpenAPI()
                        .openapi("3.0.3")
                        .info(
                                new Info()
                                        .title("Synthetic Test API")
                                        .version("1.0.0"))
                        .path(
                                "/synthetic",
                                syntheticPath);

        ContractOperation operation =
                new ContractOperation(
                        "/synthetic",
                        "GET",
                        "synthetic");

        ContractBaseline syntheticBaseline =
                new ContractBaseline(
                        "3.0.3",
                        List.of(operation),
                        syntheticOpenApi);

        var result = validator.validate(
                event(
                        "traffic-d-005",
                        5,
                        "GET",
                        "/synthetic",
                        599),
                operation,
                syntheticBaseline);

        assertEquals(
                EvaluationStatus.EVALUATED,
                result.evaluationStatus());

        assertTrue(result.findings().isEmpty());
    }
}