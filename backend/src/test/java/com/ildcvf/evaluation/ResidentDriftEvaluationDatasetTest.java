package com.ildcvf.evaluation;

import com.ildcvf.contract.service.ContractBaselineLoader;
import com.ildcvf.contract.service.ContractOperationMatcher;
import com.ildcvf.contract.service.RequestContractValidator;
import com.ildcvf.contract.service.ResponseBodyContractValidator;
import com.ildcvf.contract.service.ResponseStatusContractValidator;
import com.ildcvf.drift.domain.DriftAnalysisResult;
import com.ildcvf.drift.service.DriftAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResidentDriftEvaluationDatasetTest {

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
    }

    @Test
    void residentDatasetIsDeterministicAndStatusConsistent() {

        List<DriftEvaluationScenario> scenarios =
                ResidentDriftEvaluationDataset.scenarios();

        assertEquals(18, scenarios.size());

        Set<String> scenarioIds =
                new HashSet<>();

        for (DriftEvaluationScenario scenario : scenarios) {
            assertTrue(
                    scenarioIds.add(scenario.scenarioId()),
                    "Duplicate scenario ID: "
                            + scenario.scenarioId());
        }

        DriftEvaluationHarness harness =
                new DriftEvaluationHarness(service);

        DriftEvaluationResult first =
                harness.evaluate(scenarios);

        assertEquals(
                18,
                first.perScenarioResults().size());

        for (DriftEvaluationScenarioResult result :
                first.perScenarioResults()) {

            assertEquals(
                    result.expectedEvaluationStatus(),
                    result.actualEvaluationStatus(),
                    () -> formatFailure(result));
        }

        DriftEvaluationResult second =
                harness.evaluate(scenarios);

        assertEquals(
                first,
                second);

        assertEquals(
                3,
                first.trueNegativeScenarioCount());

        assertEquals(
                4,
                first.partiallyEvaluatedScenarioCount());

        assertEquals(
                1,
                first.notEvaluatedScenarioCount());

        System.out.println();
        System.out.println(
                "=== Resident Drift Evaluation ===");

        for (DriftEvaluationScenarioResult result :
                first.perScenarioResults()) {

            System.out.println(
                    result.scenarioId()
                            + " | expected="
                            + result.expectedEvaluationStatus()
                            + " | actual="
                            + result.actualEvaluationStatus()
                            + " | expectedKeys="
                            + result.expectedFindingKeys()
                            + " | actualKeys="
                            + result.actualFindingKeys()
                            + " | TP="
                            + result.truePositiveKeys()
                            + " | FP="
                            + result.falsePositiveKeys()
                            + " | FN="
                            + result.falseNegativeKeys());
        }

        System.out.println();
        System.out.println(
                "TP=" + first.truePositives());

        System.out.println(
                "FP=" + first.falsePositives());

        System.out.println(
                "FN=" + first.falseNegatives());

        System.out.println(
                "Precision=" + first.precision());

        System.out.println(
                "Recall=" + first.recall());

        System.out.println(
                "F1=" + first.f1());
    }

    private static String formatFailure(
            DriftEvaluationScenarioResult result) {

        return "\n"
                + "scenarioId="
                + result.scenarioId()
                + "\nexpectedStatus="
                + result.expectedEvaluationStatus()
                + "\nactualStatus="
                + result.actualEvaluationStatus()
                + "\nexpectedKeys="
                + result.expectedFindingKeys()
                + "\nactualKeys="
                + result.actualFindingKeys()
                + "\nTP="
                + result.truePositiveKeys()
                + "\nFP="
                + result.falsePositiveKeys()
                + "\nFN="
                + result.falseNegativeKeys()
                + "\nmessage="
                + result.detectorEvaluationMessage();
    }
}
