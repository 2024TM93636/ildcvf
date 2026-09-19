package com.ildcvf.contract.model;

import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.EvaluationStatus;

import java.util.List;
import java.util.Objects;

public record RequestValidationResult(
        EvaluationStatus evaluationStatus,
        List<DriftFinding> findings,
        String evaluationMessage) {

    public RequestValidationResult {
        Objects.requireNonNull(
                evaluationStatus,
                "evaluationStatus must not be null");

        Objects.requireNonNull(
                findings,
                "findings must not be null");

        findings = List.copyOf(findings);
    }

    public static RequestValidationResult evaluated(
            List<DriftFinding> findings) {

        return new RequestValidationResult(
                EvaluationStatus.EVALUATED,
                findings,
                null);
    }

    public static RequestValidationResult partiallyEvaluated(
            String message) {

        return new RequestValidationResult(
                EvaluationStatus.PARTIALLY_EVALUATED,
                List.of(),
                message);
    }
}