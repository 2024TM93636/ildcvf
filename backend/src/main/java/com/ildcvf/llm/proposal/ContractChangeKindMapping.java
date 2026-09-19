package com.ildcvf.llm.proposal;

import com.ildcvf.drift.domain.DriftType;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Explicit deterministic mapping from a frozen M4 drift type to the single
 * proposal change kind allowed by the first M7 foundation.
 */
public final class ContractChangeKindMapping {

    private static final Map<DriftType, ContractChangeKind> MAPPING =
            createMapping();

    private ContractChangeKindMapping() {
    }

    /**
     * Returns the only proposal change kind allowed for the supplied drift
     * type in the first M7 slice.
     *
     * @param driftType deterministic M4 drift type
     * @return allowed proposal change kind
     * @throws NullPointerException if {@code driftType} is null
     * @throws IllegalArgumentException if the drift type has no explicit
     *                                  M7 mapping
     */
    public static ContractChangeKind allowedChangeKind(DriftType driftType) {
        Objects.requireNonNull(driftType, "driftType must not be null");

        ContractChangeKind changeKind = MAPPING.get(driftType);

        if (changeKind == null) {
            throw new IllegalArgumentException(
                    "No ContractChangeKind mapping exists for DriftType: "
                            + driftType
            );
        }

        return changeKind;
    }

    /**
     * Deterministically checks whether a proposal change kind is allowed for
     * the supplied drift type.
     */
    public static boolean isAllowed(
            DriftType driftType,
            ContractChangeKind changeKind
    ) {
        if (driftType == null || changeKind == null) {
            return false;
        }

        return MAPPING.get(driftType) == changeKind;
    }

    /**
     * Exposes an immutable view for deterministic mapping verification.
     */
    public static Map<DriftType, ContractChangeKind> mappings() {
        return MAPPING;
    }

    private static Map<DriftType, ContractChangeKind> createMapping() {
        EnumMap<DriftType, ContractChangeKind> mapping =
                new EnumMap<>(DriftType.class);

        mapping.put(
                DriftType.UNDOCUMENTED_OPERATION,
                ContractChangeKind.ADD_OPERATION
        );

        mapping.put(
                DriftType.UNEXPECTED_REQUEST_FIELD,
                ContractChangeKind.ADD_REQUEST_FIELD
        );

        mapping.put(
                DriftType.MISSING_REQUIRED_REQUEST_FIELD,
                ContractChangeKind.RELAX_REQUEST_REQUIRED_FIELD
        );

        mapping.put(
                DriftType.REQUEST_TYPE_MISMATCH,
                ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE
        );

        mapping.put(
                DriftType.REQUEST_ENUM_MISMATCH,
                ContractChangeKind.ADD_REQUEST_ENUM_VALUE
        );

        mapping.put(
                DriftType.UNDOCUMENTED_RESPONSE_STATUS,
                ContractChangeKind.ADD_RESPONSE_STATUS
        );

        mapping.put(
                DriftType.UNEXPECTED_RESPONSE_FIELD,
                ContractChangeKind.ADD_RESPONSE_FIELD
        );

        mapping.put(
                DriftType.MISSING_REQUIRED_RESPONSE_FIELD,
                ContractChangeKind.RELAX_RESPONSE_REQUIRED_FIELD
        );

        mapping.put(
                DriftType.RESPONSE_TYPE_MISMATCH,
                ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE
        );

        mapping.put(
                DriftType.RESPONSE_ENUM_MISMATCH,
                ContractChangeKind.ADD_RESPONSE_ENUM_VALUE
        );

        return Map.copyOf(mapping);
    }
}
