package com.ildcvf.evaluation;

import com.ildcvf.contract.service.ContractBaselineLoader;
import com.ildcvf.contract.service.ContractOperationMatcher;
import com.ildcvf.contract.service.RequestContractValidator;
import com.ildcvf.contract.service.ResponseBodyContractValidator;
import com.ildcvf.contract.service.ResponseStatusContractValidator;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.drift.domain.EvaluationStatus;
import com.ildcvf.drift.service.DriftAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DriftEvaluationHarnessTest {

    private DriftAnalysisService service;
    private DriftEvaluationHarness harness;

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

        ObjectMapper objectMapper =
                new ObjectMapper();

        service = new DriftAnalysisService(
                loader,
                new ContractOperationMatcher(),
                new RequestContractValidator(objectMapper),
                new ResponseStatusContractValidator(),
                new ResponseBodyContractValidator(objectMapper));

        harness = new DriftEvaluationHarness(service);
    }

    @Test
    void nullServiceIsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> new DriftEvaluationHarness(null));
    }

    @Test
    void nullScenarioListIsRejected() {
        assertThrows(
                NullPointerException.class,
                () -> harness.evaluate(null));
    }

    @Test
    void emptyDatasetProducesZeroMetrics() {
        DriftEvaluationResult result =
                harness.evaluate(List.of());

        assertEquals(0, result.truePositives());
        assertEquals(0, result.falsePositives());
        assertEquals(0, result.falseNegatives());

        assertEquals(0.0, result.precision());
        assertEquals(0.0, result.recall());
        assertEquals(0.0, result.f1());

        assertEquals(0, result.evaluatedScenarioCount());
        assertEquals(0, result.partiallyEvaluatedScenarioCount());
        assertEquals(0, result.notEvaluatedScenarioCount());
        assertEquals(0, result.trueNegativeScenarioCount());

        assertTrue(result.perScenarioResults().isEmpty());
    }

    @Test
    void validScenarioProducesTrueNegative() {
        DriftEvaluationScenario scenario =
                new DriftEvaluationScenario(
                        "harness-001",
                        "Valid documented POST.",
                        ResidentTrafficEventFixtures.baselineValidPost(),
                        EvaluationStatus.EVALUATED,
                        List.of());

        DriftEvaluationResult result =
                harness.evaluate(List.of(scenario));

        assertEquals(1, result.evaluatedScenarioCount());
        assertEquals(0, result.partiallyEvaluatedScenarioCount());
        assertEquals(0, result.notEvaluatedScenarioCount());

        assertEquals(1, result.trueNegativeScenarioCount());

        assertEquals(0, result.truePositives());
        assertEquals(0, result.falsePositives());
        assertEquals(0, result.falseNegatives());

        assertEquals(0.0, result.precision());
        assertEquals(0.0, result.recall());
        assertEquals(0.0, result.f1());
    }

    @Test
    void matchingFindingProducesTruePositive() {
        DriftEvaluationScenario scenario =
                new DriftEvaluationScenario(
                        "harness-002",
                        "Undocumented operation.",
                        ResidentTrafficEventFixtures.undocumentedOperation(),
                        EvaluationStatus.EVALUATED,
                        List.of(
                                new DriftFindingKey(
                                        DriftType.UNDOCUMENTED_OPERATION,
                                        null)));

        DriftEvaluationResult result =
                harness.evaluate(List.of(scenario));

        assertEquals(1, result.truePositives());
        assertEquals(0, result.falsePositives());
        assertEquals(0, result.falseNegatives());

        assertEquals(1.0, result.precision());
        assertEquals(1.0, result.recall());
        assertEquals(1.0, result.f1());

        DriftEvaluationScenarioResult scenarioResult =
                result.perScenarioResults().getFirst();

        assertEquals(
                scenarioResult.expectedFindingKeys(),
                scenarioResult.truePositiveKeys());

        assertTrue(
                scenarioResult.falsePositiveKeys().isEmpty());

        assertTrue(
                scenarioResult.falseNegativeKeys().isEmpty());
    }

    @Test
    void unexpectedActualFindingProducesFalsePositive() {
        DriftEvaluationScenario scenario =
                new DriftEvaluationScenario(
                        "harness-003",
                        "Unexpected detector finding.",
                        ResidentTrafficEventFixtures.undocumentedOperation(),
                        EvaluationStatus.EVALUATED,
                        List.of());

        DriftEvaluationResult result =
                harness.evaluate(List.of(scenario));

        assertEquals(0, result.truePositives());
        assertEquals(1, result.falsePositives());
        assertEquals(0, result.falseNegatives());

        assertEquals(0.0, result.precision());
        assertEquals(0.0, result.recall());
        assertEquals(0.0, result.f1());

        assertEquals(
                List.of(
                        new DriftFindingKey(
                                DriftType.UNDOCUMENTED_OPERATION,
                                null)),
                result.perScenarioResults()
                        .getFirst()
                        .falsePositiveKeys());
    }

    @Test
    void expectedMissingFindingProducesFalseNegative() {
        DriftEvaluationScenario scenario =
                new DriftEvaluationScenario(
                        "harness-004",
                        "Expected finding is absent.",
                        ResidentTrafficEventFixtures.baselineValidPost(),
                        EvaluationStatus.EVALUATED,
                        List.of(
                                new DriftFindingKey(
                                        DriftType.UNDOCUMENTED_OPERATION,
                                        null)));

        DriftEvaluationResult result =
                harness.evaluate(List.of(scenario));

        assertEquals(0, result.truePositives());
        assertEquals(0, result.falsePositives());
        assertEquals(1, result.falseNegatives());

        assertEquals(0.0, result.precision());
        assertEquals(0.0, result.recall());
        assertEquals(0.0, result.f1());
    }

    @Test
    void partiallyEvaluatedScenarioDoesNotAffectFindingMetrics() {
        DriftEvaluationScenario scenario =
                new DriftEvaluationScenario(
                        "harness-005",
                        "Truncated request.",
                        ResidentTrafficEventFixtures.requestTruncated(),
                        EvaluationStatus.PARTIALLY_EVALUATED,
                        List.of());

        DriftEvaluationResult result =
                harness.evaluate(List.of(scenario));

        assertEquals(0, result.truePositives());
        assertEquals(0, result.falsePositives());
        assertEquals(0, result.falseNegatives());

        assertEquals(0, result.evaluatedScenarioCount());
        assertEquals(1, result.partiallyEvaluatedScenarioCount());
        assertEquals(0, result.notEvaluatedScenarioCount());

        assertEquals(0, result.trueNegativeScenarioCount());
    }

    @Test
    void notEvaluatedScenarioDoesNotAffectFindingMetrics() {
        DriftEvaluationScenario scenario =
                new DriftEvaluationScenario(
                        "harness-006",
                        "Upstream was not reached.",
                        ResidentTrafficEventFixtures.upstreamNotReached(),
                        EvaluationStatus.NOT_EVALUATED,
                        List.of());

        DriftEvaluationResult result =
                harness.evaluate(List.of(scenario));

        assertEquals(0, result.truePositives());
        assertEquals(0, result.falsePositives());
        assertEquals(0, result.falseNegatives());

        assertEquals(0, result.evaluatedScenarioCount());
        assertEquals(0, result.partiallyEvaluatedScenarioCount());
        assertEquals(1, result.notEvaluatedScenarioCount());

        assertEquals(0, result.trueNegativeScenarioCount());
    }

    @Test
    void statusMismatchDoesNotProduceFindingMetrics() {
        DriftEvaluationScenario scenario =
                new DriftEvaluationScenario(
                        "harness-007",
                        "Expected evaluated but detector returns partial.",
                        ResidentTrafficEventFixtures.requestTruncated(),
                        EvaluationStatus.EVALUATED,
                        List.of());

        DriftEvaluationResult result =
                harness.evaluate(List.of(scenario));

        DriftEvaluationScenarioResult scenarioResult =
                result.perScenarioResults().getFirst();

        assertEquals(
                EvaluationStatus.EVALUATED,
                scenarioResult.expectedEvaluationStatus());

        assertEquals(
                EvaluationStatus.PARTIALLY_EVALUATED,
                scenarioResult.actualEvaluationStatus());

        assertTrue(
                scenarioResult.truePositiveKeys().isEmpty());

        assertTrue(
                scenarioResult.falsePositiveKeys().isEmpty());

        assertTrue(
                scenarioResult.falseNegativeKeys().isEmpty());

        assertNotNull(
                scenarioResult.detectorEvaluationMessage());

        assertEquals(0, result.truePositives());
        assertEquals(0, result.falsePositives());
        assertEquals(0, result.falseNegatives());
    }

    @Test
    void duplicateScenarioIdsAreRejected() {
        DriftEvaluationScenario first =
                new DriftEvaluationScenario(
                        "duplicate-id",
                        "First.",
                        ResidentTrafficEventFixtures.baselineValidPost(),
                        EvaluationStatus.EVALUATED,
                        List.of());

        DriftEvaluationScenario second =
                new DriftEvaluationScenario(
                        "duplicate-id",
                        "Second.",
                        ResidentTrafficEventFixtures.baselineValidGetItem(),
                        EvaluationStatus.EVALUATED,
                        List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> harness.evaluate(
                        List.of(first, second)));
    }

    @Test
    void duplicateExpectedFindingKeysAreRejected() {
        DriftFindingKey key =
                new DriftFindingKey(
                        DriftType.UNDOCUMENTED_OPERATION,
                        null);

        DriftEvaluationScenario scenario =
                new DriftEvaluationScenario(
                        "harness-008",
                        "Duplicate expected finding.",
                        ResidentTrafficEventFixtures.baselineValidPost(),
                        EvaluationStatus.EVALUATED,
                        List.of(key, key));

        assertThrows(
                IllegalStateException.class,
                () -> harness.evaluate(List.of(scenario)));
    }

    @Test
    void actualFindingKeysAreSortedDeterministically() {
        DriftEvaluationScenario scenario =
                new DriftEvaluationScenario(
                        "harness-009",
                        "Multiple response findings.",
                        ResidentTrafficEventFixtures.collectionItemTypeMismatch(),
                        EvaluationStatus.EVALUATED,
                        List.of(
                                new DriftFindingKey(
                                        DriftType.RESPONSE_TYPE_MISMATCH,
                                        "[0].id")));

        DriftEvaluationResult first =
                harness.evaluate(List.of(scenario));

        DriftEvaluationResult second =
                harness.evaluate(List.of(scenario));

        assertEquals(first, second);

        assertEquals(
                first.perScenarioResults()
                        .getFirst()
                        .actualFindingKeys(),
                second.perScenarioResults()
                        .getFirst()
                        .actualFindingKeys());
    }

    @Test
    void mixedDatasetAggregatesMetricsAcrossScenarios() {
        DriftEvaluationScenario truePositive =
                new DriftEvaluationScenario(
                        "harness-010",
                        "True positive.",
                        ResidentTrafficEventFixtures.undocumentedOperation(),
                        EvaluationStatus.EVALUATED,
                        List.of(
                                new DriftFindingKey(
                                        DriftType.UNDOCUMENTED_OPERATION,
                                        null)));

        DriftEvaluationScenario trueNegative =
                new DriftEvaluationScenario(
                        "harness-011",
                        "True negative.",
                        ResidentTrafficEventFixtures.baselineValidPost(),
                        EvaluationStatus.EVALUATED,
                        List.of());

        DriftEvaluationScenario falsePositive =
                new DriftEvaluationScenario(
                        "harness-012",
                        "False positive.",
                        ResidentTrafficEventFixtures.undocumentedOperation(),
                        EvaluationStatus.EVALUATED,
                        List.of());

        DriftEvaluationScenario falseNegative =
                new DriftEvaluationScenario(
                        "harness-013",
                        "False negative.",
                        ResidentTrafficEventFixtures.baselineValidPost(),
                        EvaluationStatus.EVALUATED,
                        List.of(
                                new DriftFindingKey(
                                        DriftType.UNDOCUMENTED_OPERATION,
                                        null)));

        DriftEvaluationResult result =
                harness.evaluate(
                        List.of(
                                truePositive,
                                trueNegative,
                                falsePositive,
                                falseNegative));

        assertEquals(1, result.truePositives());
        assertEquals(1, result.falsePositives());
        assertEquals(1, result.falseNegatives());

        assertEquals(
                0.5,
                result.precision(),
                0.0000001);

        assertEquals(
                0.5,
                result.recall(),
                0.0000001);

        assertEquals(
                0.5,
                result.f1(),
                0.0000001);

        assertEquals(4, result.evaluatedScenarioCount());
        assertEquals(1, result.trueNegativeScenarioCount());
    }


}