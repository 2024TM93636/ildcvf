package com.ildcvf.experiment.llm.groq;

import java.util.Objects;

public record TransportDiagnostic(
        String transportFailureKind,
        String transportExceptionClass,
        String transportCauseClass,
        TransportDiagnosticCode transportDiagnosticCode
) {

    public TransportDiagnostic {
        Objects.requireNonNull(
                transportFailureKind,
                "transportFailureKind must not be null"
        );

        Objects.requireNonNull(
                transportExceptionClass,
                "transportExceptionClass must not be null"
        );

        Objects.requireNonNull(
                transportDiagnosticCode,
                "transportDiagnosticCode must not be null"
        );
    }
}