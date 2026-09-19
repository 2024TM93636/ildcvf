package com.ildcvf.llm.evidence;

import com.ildcvf.drift.domain.DriftSeverity;
import com.ildcvf.drift.domain.DriftType;
import com.ildcvf.llm.support.M7TestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidenceFingerprintTest {

    @Test
    void sameEvidenceProducesSameFingerprint() {
        GroundedEvidencePackage evidence =
                M7TestFixtures.evidence(
                        DriftType.REQUEST_TYPE_MISMATCH
                );

        String first =
                EvidenceFingerprint.sha256(evidence);

        String second =
                EvidenceFingerprint.sha256(evidence);

        assertEquals(first, second);
    }

    @Test
    void fingerprintIsLowercaseSha256Hexadecimal() {
        String fingerprint =
                EvidenceFingerprint.sha256(
                        M7TestFixtures.evidence(
                                DriftType.REQUEST_TYPE_MISMATCH
                        )
                );

        assertEquals(64, fingerprint.length());
        assertTrue(fingerprint.matches("[0-9a-f]{64}"));
    }

    @Test
    void representativeEvidenceDimensionsAffectFingerprint() {
        GroundedEvidencePackage base =
                M7TestFixtures.evidence(
                        DriftType.REQUEST_TYPE_MISMATCH
                );

        String baseFingerprint =
                EvidenceFingerprint.sha256(base);

        List<UnaryOperator<GroundedEvidencePackage>> mutations =
                List.of(
                        evidence -> copy(
                                evidence,
                                "traffic-different",
                                evidence.trafficSequence(),
                                evidence.findingId(),
                                evidence.driftType(),
                                evidence.severity(),
                                evidence.method(),
                                evidence.runtimePath(),
                                evidence.matchedOpenApiPath(),
                                evidence.fieldPath(),
                                evidence.expected(),
                                evidence.observed(),
                                evidence.findingMessage(),
                                evidence.relevantContractContext()
                        ),
                        evidence -> copy(
                                evidence,
                                evidence.trafficEventId(),
                                evidence.trafficSequence(),
                                evidence.findingId(),
                                DriftType.RESPONSE_TYPE_MISMATCH,
                                evidence.severity(),
                                evidence.method(),
                                evidence.runtimePath(),
                                evidence.matchedOpenApiPath(),
                                evidence.fieldPath(),
                                evidence.expected(),
                                evidence.observed(),
                                evidence.findingMessage(),
                                evidence.relevantContractContext()
                        ),
                        evidence -> copy(
                                evidence,
                                evidence.trafficEventId(),
                                evidence.trafficSequence(),
                                evidence.findingId(),
                                evidence.driftType(),
                                DriftSeverity.WARNING,
                                evidence.method(),
                                evidence.runtimePath(),
                                evidence.matchedOpenApiPath(),
                                evidence.fieldPath(),
                                evidence.expected(),
                                evidence.observed(),
                                evidence.findingMessage(),
                                evidence.relevantContractContext()
                        ),
                        evidence -> copy(
                                evidence,
                                evidence.trafficEventId(),
                                evidence.trafficSequence(),
                                evidence.findingId(),
                                evidence.driftType(),
                                evidence.severity(),
                                "PUT",
                                evidence.runtimePath(),
                                evidence.matchedOpenApiPath(),
                                evidence.fieldPath(),
                                evidence.expected(),
                                evidence.observed(),
                                evidence.findingMessage(),
                                evidence.relevantContractContext()
                        ),
                        evidence -> copy(
                                evidence,
                                evidence.trafficEventId(),
                                evidence.trafficSequence(),
                                evidence.findingId(),
                                evidence.driftType(),
                                evidence.severity(),
                                evidence.method(),
                                "/api/residents/101",
                                evidence.matchedOpenApiPath(),
                                evidence.fieldPath(),
                                evidence.expected(),
                                evidence.observed(),
                                evidence.findingMessage(),
                                evidence.relevantContractContext()
                        ),
                        evidence -> copy(
                                evidence,
                                evidence.trafficEventId(),
                                evidence.trafficSequence(),
                                evidence.findingId(),
                                evidence.driftType(),
                                evidence.severity(),
                                evidence.method(),
                                evidence.runtimePath(),
                                evidence.matchedOpenApiPath(),
                                "$.different",
                                evidence.expected(),
                                evidence.observed(),
                                evidence.findingMessage(),
                                evidence.relevantContractContext()
                        ),
                        evidence -> copy(
                                evidence,
                                evidence.trafficEventId(),
                                evidence.trafficSequence(),
                                evidence.findingId(),
                                evidence.driftType(),
                                evidence.severity(),
                                evidence.method(),
                                evidence.runtimePath(),
                                evidence.matchedOpenApiPath(),
                                evidence.fieldPath(),
                                "different-expected",
                                evidence.observed(),
                                evidence.findingMessage(),
                                evidence.relevantContractContext()
                        ),
                        evidence -> copy(
                                evidence,
                                evidence.trafficEventId(),
                                evidence.trafficSequence(),
                                evidence.findingId(),
                                evidence.driftType(),
                                evidence.severity(),
                                evidence.method(),
                                evidence.runtimePath(),
                                evidence.matchedOpenApiPath(),
                                evidence.fieldPath(),
                                evidence.expected(),
                                "different-observed",
                                evidence.findingMessage(),
                                evidence.relevantContractContext()
                        ),
                        evidence -> copy(
                                evidence,
                                evidence.trafficEventId(),
                                evidence.trafficSequence(),
                                evidence.findingId(),
                                evidence.driftType(),
                                evidence.severity(),
                                evidence.method(),
                                evidence.runtimePath(),
                                evidence.matchedOpenApiPath(),
                                evidence.fieldPath(),
                                evidence.expected(),
                                evidence.observed(),
                                "different-message",
                                evidence.relevantContractContext()
                        ),
                        evidence -> copy(
                                evidence,
                                evidence.trafficEventId(),
                                evidence.trafficSequence(),
                                evidence.findingId(),
                                evidence.driftType(),
                                evidence.severity(),
                                evidence.method(),
                                evidence.runtimePath(),
                                evidence.matchedOpenApiPath(),
                                evidence.fieldPath(),
                                evidence.expected(),
                                evidence.observed(),
                                evidence.findingMessage(),
                                new RelevantContractContext(
                                        "3.1.1",
                                        evidence.method(),
                                        evidence.matchedOpenApiPath(),
                                        "differentOperation"
                                )
                        )
                );

        for (UnaryOperator<GroundedEvidencePackage> mutation
                : mutations) {
            GroundedEvidencePackage changed =
                    mutation.apply(base);

            assertNotEquals(
                    baseFingerprint,
                    EvidenceFingerprint.sha256(changed)
            );
        }
    }

    @Test
    void nullAndEmptyStringProduceDifferentFingerprints() {
        GroundedEvidencePackage base =
                M7TestFixtures.evidence(
                        DriftType.REQUEST_TYPE_MISMATCH
                );

        GroundedEvidencePackage withNull = copy(
                base,
                base.trafficEventId(),
                base.trafficSequence(),
                base.findingId(),
                base.driftType(),
                base.severity(),
                base.method(),
                base.runtimePath(),
                base.matchedOpenApiPath(),
                null,
                base.expected(),
                base.observed(),
                base.findingMessage(),
                base.relevantContractContext()
        );

        GroundedEvidencePackage withEmpty = copy(
                base,
                base.trafficEventId(),
                base.trafficSequence(),
                base.findingId(),
                base.driftType(),
                base.severity(),
                base.method(),
                base.runtimePath(),
                base.matchedOpenApiPath(),
                "",
                base.expected(),
                base.observed(),
                base.findingMessage(),
                base.relevantContractContext()
        );

        assertNotEquals(
                EvidenceFingerprint.sha256(withNull),
                EvidenceFingerprint.sha256(withEmpty)
        );
    }

    @Test
    void delimiterAndNewlineContentRemainsDeterministicAndDistinct() {
        GroundedEvidencePackage base =
                M7TestFixtures.evidence(
                        DriftType.REQUEST_TYPE_MISMATCH
                );

        GroundedEvidencePackage first = copy(
                base,
                base.trafficEventId(),
                base.trafficSequence(),
                base.findingId(),
                base.driftType(),
                base.severity(),
                base.method(),
                base.runtimePath(),
                base.matchedOpenApiPath(),
                "$.field\nnext=value|type:string",
                base.expected(),
                base.observed(),
                base.findingMessage(),
                base.relevantContractContext()
        );

        GroundedEvidencePackage same = copy(
                base,
                base.trafficEventId(),
                base.trafficSequence(),
                base.findingId(),
                base.driftType(),
                base.severity(),
                base.method(),
                base.runtimePath(),
                base.matchedOpenApiPath(),
                "$.field\nnext=value|type:string",
                base.expected(),
                base.observed(),
                base.findingMessage(),
                base.relevantContractContext()
        );

        GroundedEvidencePackage distinct = copy(
                base,
                base.trafficEventId(),
                base.trafficSequence(),
                base.findingId(),
                base.driftType(),
                base.severity(),
                base.method(),
                base.runtimePath(),
                base.matchedOpenApiPath(),
                "$.field",
                "next=value|type:string\n" + base.expected(),
                base.observed(),
                base.findingMessage(),
                base.relevantContractContext()
        );

        assertEquals(
                EvidenceFingerprint.sha256(first),
                EvidenceFingerprint.sha256(same)
        );

        assertNotEquals(
                EvidenceFingerprint.sha256(first),
                EvidenceFingerprint.sha256(distinct)
        );
    }

    @Test
    void absentContextDiffersFromPresentAllNullContext() {
        GroundedEvidencePackage base =
                M7TestFixtures.evidence(
                        DriftType.REQUEST_TYPE_MISMATCH
                );

        GroundedEvidencePackage absent = copy(
                base,
                base.trafficEventId(),
                base.trafficSequence(),
                base.findingId(),
                base.driftType(),
                base.severity(),
                base.method(),
                base.runtimePath(),
                base.matchedOpenApiPath(),
                base.fieldPath(),
                base.expected(),
                base.observed(),
                base.findingMessage(),
                null
        );

        GroundedEvidencePackage presentButEmpty = copy(
                base,
                base.trafficEventId(),
                base.trafficSequence(),
                base.findingId(),
                base.driftType(),
                base.severity(),
                base.method(),
                base.runtimePath(),
                base.matchedOpenApiPath(),
                base.fieldPath(),
                base.expected(),
                base.observed(),
                base.findingMessage(),
                new RelevantContractContext(
                        null,
                        null,
                        null,
                        null
                )
        );

        assertNotEquals(
                EvidenceFingerprint.sha256(absent),
                EvidenceFingerprint.sha256(presentButEmpty)
        );
    }

    private GroundedEvidencePackage copy(
            GroundedEvidencePackage source,
            String trafficEventId,
            long trafficSequence,
            String findingId,
            DriftType driftType,
            DriftSeverity severity,
            String method,
            String runtimePath,
            String matchedOpenApiPath,
            String fieldPath,
            String expected,
            String observed,
            String findingMessage,
            RelevantContractContext relevantContractContext
    ) {
        return new GroundedEvidencePackage(
                trafficEventId,
                trafficSequence,
                findingId,
                driftType,
                severity,
                method,
                runtimePath,
                matchedOpenApiPath,
                fieldPath,
                expected,
                observed,
                findingMessage,
                relevantContractContext
        );
    }
}