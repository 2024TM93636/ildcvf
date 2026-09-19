package com.ildcvf.llm.evidence;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Produces a stable SHA-256 identity for one GroundedEvidencePackage.
 *
 * <p>The canonical representation is constructed explicitly and does not
 * depend on record.toString(), JSON/Jackson serialization, Java object
 * serialization, or raw TrafficEvent data.</p>
 *
 * <p>Arbitrary string values are encoded using explicit UTF-8 byte lengths
 * and null markers, making field boundaries unambiguous even when values
 * contain newlines or delimiter characters.</p>
 */
public final class EvidenceFingerprint {

    private static final String SCHEMA_VERSION = "m7-evidence-v1";
    private static final String HASH_ALGORITHM = "SHA-256";

    private EvidenceFingerprint() {
    }

    /**
     * Returns the lowercase hexadecimal SHA-256 fingerprint of the complete
     * bounded evidence package.
     *
     * @param evidence grounded evidence to fingerprint
     * @return 64-character lowercase SHA-256 hexadecimal value
     */
    public static String sha256(GroundedEvidencePackage evidence) {
        Objects.requireNonNull(evidence, "evidence must not be null");

        byte[] canonicalBytes = canonicalBytes(evidence);

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(HASH_ALGORITHM);

            return HexFormat.of().formatHex(
                    digest.digest(canonicalBytes)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Required hash algorithm is unavailable: "
                            + HASH_ALGORITHM,
                    exception
            );
        }
    }

    private static byte[] canonicalBytes(
            GroundedEvidencePackage evidence
    ) {
        try {
            ByteArrayOutputStream byteStream =
                    new ByteArrayOutputStream();

            try (DataOutputStream output =
                         new DataOutputStream(byteStream)) {

                writeStringField(
                        output,
                        "schema",
                        SCHEMA_VERSION
                );

                writeStringField(
                        output,
                        "trafficEventId",
                        evidence.trafficEventId()
                );

                writeStringField(
                        output,
                        "trafficSequence",
                        Long.toString(evidence.trafficSequence())
                );

                writeStringField(
                        output,
                        "findingId",
                        evidence.findingId()
                );

                writeStringField(
                        output,
                        "driftType",
                        enumName(evidence.driftType())
                );

                writeStringField(
                        output,
                        "severity",
                        enumName(evidence.severity())
                );

                writeStringField(
                        output,
                        "method",
                        evidence.method()
                );

                writeStringField(
                        output,
                        "runtimePath",
                        evidence.runtimePath()
                );

                writeStringField(
                        output,
                        "matchedOpenApiPath",
                        evidence.matchedOpenApiPath()
                );

                writeStringField(
                        output,
                        "fieldPath",
                        evidence.fieldPath()
                );

                writeStringField(
                        output,
                        "expected",
                        evidence.expected()
                );

                writeStringField(
                        output,
                        "observed",
                        evidence.observed()
                );

                writeStringField(
                        output,
                        "findingMessage",
                        evidence.findingMessage()
                );

                RelevantContractContext context =
                        evidence.relevantContractContext();

                writeBooleanField(
                        output,
                        "relevantContractContextPresent",
                        context != null
                );

                writeStringField(
                        output,
                        "contract.openApiVersion",
                        context == null
                                ? null
                                : context.openApiVersion()
                );

                writeStringField(
                        output,
                        "contract.method",
                        context == null
                                ? null
                                : context.method()
                );

                writeStringField(
                        output,
                        "contract.pathTemplate",
                        context == null
                                ? null
                                : context.pathTemplate()
                );

                writeStringField(
                        output,
                        "contract.operationId",
                        context == null
                                ? null
                                : context.operationId()
                );

                output.flush();
            }

            return byteStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to construct canonical grounded evidence bytes",
                    exception
            );
        }
    }

    private static void writeBooleanField(
            DataOutputStream output,
            String fieldName,
            boolean value
    ) throws IOException {
        writeFieldName(output, fieldName);
        output.writeByte(1);
        output.writeBoolean(value);
    }

    private static void writeStringField(
            DataOutputStream output,
            String fieldName,
            String value
    ) throws IOException {
        writeFieldName(output, fieldName);

        if (value == null) {
            output.writeByte(0);
            return;
        }

        output.writeByte(1);

        byte[] valueBytes =
                value.getBytes(StandardCharsets.UTF_8);

        output.writeInt(valueBytes.length);
        output.write(valueBytes);
    }

    private static void writeFieldName(
            DataOutputStream output,
            String fieldName
    ) throws IOException {
        byte[] fieldNameBytes =
                fieldName.getBytes(StandardCharsets.UTF_8);

        output.writeInt(fieldNameBytes.length);
        output.write(fieldNameBytes);
    }

    private static String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
