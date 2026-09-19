package com.ildcvf.contract.model;

import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.EvaluationStatus;

import java.util.List;
import java.util.Objects;

public record ResponseStatusValidationResult(
        EvaluationStatus evaluationStatus,
        List<DriftFinding> findings,
        String evaluationMessage) {

    public ResponseStatusValidationResult {
        Objects.requireNonNull(
                evaluationStatus,
                "evaluationStatus must not be null");

        Objects.requireNonNull(
                findings,
                "findings must not be null");

        findings = List.copyOf(findings);
    }

    public static ResponseStatusValidationResult evaluated(
            List<DriftFinding> findings) {

        return new ResponseStatusValidationResult(
                EvaluationStatus.EVALUATED,
                findings,
                null);
    }

    public static ResponseStatusValidationResult partiallyEvaluated(
            String message) {

        return new ResponseStatusValidationResult(
                EvaluationStatus.PARTIALLY_EVALUATED,
                List.of(),
                message);
    }
}