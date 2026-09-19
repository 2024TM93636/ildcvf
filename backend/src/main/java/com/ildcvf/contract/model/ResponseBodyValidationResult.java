package com.ildcvf.contract.model;

import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.EvaluationStatus;

import java.util.List;
import java.util.Objects;

public record ResponseBodyValidationResult(
        EvaluationStatus evaluationStatus,
        List<DriftFinding> findings,
        String evaluationMessage) {

    public ResponseBodyValidationResult {
        Objects.requireNonNull(
                evaluationStatus,
                "evaluationStatus must not be null");

        Objects.requireNonNull(
                findings,
                "findings must not be null");

        findings = List.copyOf(findings);
    }

    public static ResponseBodyValidationResult evaluated(
            List<DriftFinding> findings) {

        return new ResponseBodyValidationResult(
                EvaluationStatus.EVALUATED,
                findings,
                null);
    }

    public static ResponseBodyValidationResult partiallyEvaluated(
            String message) {

        return new ResponseBodyValidationResult(
                EvaluationStatus.PARTIALLY_EVALUATED,
                List.of(),
                message);
    }
}