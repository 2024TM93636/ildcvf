package com.ildcvf.evaluation;

import com.ildcvf.drift.domain.DriftAnalysisResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.EvaluationStatus;
import com.ildcvf.drift.service.DriftAnalysisService;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DriftEvaluationHarness {

    private static final Comparator<DriftFindingKey> KEY_COMPARATOR =
            Comparator
                    .comparing(
                            (DriftFindingKey key) ->
                                    key.type().name())
                    .thenComparing(
                            DriftFindingKey::fieldPath);

    private final DriftAnalysisService driftAnalysisService;

    public DriftEvaluationHarness(
            DriftAnalysisService driftAnalysisService) {

        this.driftAnalysisService =
                Objects.requireNonNull(
                        driftAnalysisService,
                        "driftAnalysisService");
    }

    public DriftEvaluationResult evaluate(
            List<DriftEvaluationScenario> scenarios) {

        Objects.requireNonNull(
                scenarios,
                "scenarios");

        validateScenarioIds(scenarios);

        List<DriftEvaluationScenarioResult> scenarioResults =
                scenarios.stream()
                        .map(this::evaluateScenario)
                        .toList();

        return aggregate(scenarioResults);
    }

    private DriftEvaluationScenarioResult evaluateScenario(
            DriftEvaluationScenario scenario) {

        DriftAnalysisResult analysis =
                driftAnalysisService.analyze(
                        scenario.event());

        EvaluationStatus actualStatus =
                analysis.evaluationStatus();

        List<DriftFindingKey> expectedKeys =
                sorted(scenario.expectedFindingKeys());

        assertNoDuplicateKeys(
                scenario.scenarioId(),
                "expected",
                expectedKeys);

        List<DriftFindingKey> actualKeys =
                normalizeActualFindings(
                        scenario.scenarioId(),
                        analysis.findings());

        if (scenario.expectedEvaluationStatus()
                != actualStatus) {

            return new DriftEvaluationScenarioResult(
                    scenario.scenarioId(),
                    scenario.expectedEvaluationStatus(),
                    actualStatus,
                    expectedKeys,
                    actualKeys,
                    List.of(),
                    List.of(),
                    List.of(),
                    analysis.evaluationMessage());
        }

        /*
         * NOT_EVALUATED and PARTIALLY_EVALUATED are tracked
         * separately. They do not participate in TP/FP/FN.
         */
        if (actualStatus != EvaluationStatus.EVALUATED) {

            return new DriftEvaluationScenarioResult(
                    scenario.scenarioId(),
                    scenario.expectedEvaluationStatus(),
                    actualStatus,
                    expectedKeys,
                    actualKeys,
                    List.of(),
                    List.of(),
                    List.of(),
                    "EvaluationStatus mismatch: expected "
                            + scenario.expectedEvaluationStatus()
                            + " but actual was "
                            + actualStatus
                            + (analysis.evaluationMessage() == null
                            ? ""
                            : " | " + analysis.evaluationMessage()));
        }

        Set<DriftFindingKey> expected =
                new LinkedHashSet<>(expectedKeys);

        Set<DriftFindingKey> actual =
                new LinkedHashSet<>(actualKeys);

        Set<DriftFindingKey> truePositives =
                new LinkedHashSet<>(expected);
        truePositives.retainAll(actual);

        Set<DriftFindingKey> falsePositives =
                new LinkedHashSet<>(actual);
        falsePositives.removeAll(expected);

        Set<DriftFindingKey> falseNegatives =
                new LinkedHashSet<>(expected);
        falseNegatives.removeAll(actual);

        return new DriftEvaluationScenarioResult(
                scenario.scenarioId(),
                scenario.expectedEvaluationStatus(),
                actualStatus,
                expectedKeys,
                actualKeys,
                sorted(truePositives),
                sorted(falsePositives),
                sorted(falseNegatives),
                analysis.evaluationMessage());
    }

    private static List<DriftFindingKey> normalizeActualFindings(
            String scenarioId,
            List<DriftFinding> findings) {

        List<DriftFindingKey> keys =
                findings.stream()
                        .map(DriftFindingKey::from)
                        .toList();

        assertNoDuplicateKeys(
                scenarioId,
                "actual",
                keys);

        return sorted(keys);
    }

    private static void assertNoDuplicateKeys(
            String scenarioId,
            String source,
            List<DriftFindingKey> keys) {

        Map<DriftFindingKey, Long> frequencies =
                keys.stream()
                        .collect(Collectors.groupingBy(
                                Function.identity(),
                                LinkedHashMap::new,
                                Collectors.counting()));

        frequencies.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .findFirst()
                .ifPresent(entry -> {
                    throw new IllegalStateException(
                            "Duplicate " + source
                                    + " finding key for scenario "
                                    + scenarioId
                                    + ": "
                                    + entry.getKey());
                });
    }

    private static List<DriftFindingKey> sorted(
            Collection<DriftFindingKey> keys) {

        return keys.stream()
                .sorted(KEY_COMPARATOR)
                .toList();
    }

    private static void validateScenarioIds(
            List<DriftEvaluationScenario> scenarios) {

        Set<String> ids = new HashSet<>();

        for (DriftEvaluationScenario scenario : scenarios) {
            if (!ids.add(scenario.scenarioId())) {
                throw new IllegalArgumentException(
                        "Duplicate scenarioId: "
                                + scenario.scenarioId());
            }
        }
    }

    private static DriftEvaluationResult aggregate(
            List<DriftEvaluationScenarioResult> results) {

        long truePositives =
                results.stream()
                        .mapToLong(result ->
                                result.truePositiveKeys().size())
                        .sum();

        long falsePositives =
                results.stream()
                        .mapToLong(result ->
                                result.falsePositiveKeys().size())
                        .sum();

        long falseNegatives =
                results.stream()
                        .mapToLong(result ->
                                result.falseNegativeKeys().size())
                        .sum();

        long evaluatedScenarioCount =
                results.stream()
                        .filter(result ->
                                result.actualEvaluationStatus()
                                        == EvaluationStatus.EVALUATED)
                        .count();

        long partiallyEvaluatedScenarioCount =
                results.stream()
                        .filter(result ->
                                result.actualEvaluationStatus()
                                        == EvaluationStatus.PARTIALLY_EVALUATED)
                        .count();

        long notEvaluatedScenarioCount =
                results.stream()
                        .filter(result ->
                                result.actualEvaluationStatus()
                                        == EvaluationStatus.NOT_EVALUATED)
                        .count();

        long trueNegativeScenarioCount =
                results.stream()
                        .filter(DriftEvaluationHarness::isTrueNegative)
                        .count();

        double precision =
                truePositives + falsePositives == 0
                        ? 0.0
                        : (double) truePositives
                        / (truePositives + falsePositives);

        double recall =
                truePositives + falseNegatives == 0
                        ? 0.0
                        : (double) truePositives
                        / (truePositives + falseNegatives);

        double f1 =
                precision + recall == 0.0
                        ? 0.0
                        : 2.0 * precision * recall
                        / (precision + recall);

        return new DriftEvaluationResult(
                truePositives,
                falsePositives,
                falseNegatives,
                precision,
                recall,
                f1,
                evaluatedScenarioCount,
                partiallyEvaluatedScenarioCount,
                notEvaluatedScenarioCount,
                trueNegativeScenarioCount,
                results);
    }

    private static boolean isTrueNegative(
            DriftEvaluationScenarioResult result) {

        return result.expectedEvaluationStatus()
                == EvaluationStatus.EVALUATED
                && result.actualEvaluationStatus()
                == EvaluationStatus.EVALUATED
                && result.expectedFindingKeys().isEmpty()
                && result.actualFindingKeys().isEmpty();
    }
}
