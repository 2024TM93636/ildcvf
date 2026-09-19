package com.ildcvf.evaluation;

import java.util.List;

public record DriftEvaluationResult(
        long truePositives,
        long falsePositives,
        long falseNegatives,

        double precision,
        double recall,
        double f1,

        long evaluatedScenarioCount,
        long partiallyEvaluatedScenarioCount,
        long notEvaluatedScenarioCount,
        long trueNegativeScenarioCount,

        List<DriftEvaluationScenarioResult> perScenarioResults
) {
    public DriftEvaluationResult {
        perScenarioResults = List.copyOf(perScenarioResults);
    }
}
