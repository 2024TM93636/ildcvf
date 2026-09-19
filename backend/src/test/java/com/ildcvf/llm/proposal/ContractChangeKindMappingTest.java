package com.ildcvf.llm.proposal;

import com.ildcvf.drift.domain.DriftType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractChangeKindMappingTest {

    @Test
    void mapsAllTenFrozenDriftTypesExactly() {
        assertEquals(
                ContractChangeKind.ADD_OPERATION,
                ContractChangeKindMapping.allowedChangeKind(
                        DriftType.UNDOCUMENTED_OPERATION
                )
        );

        assertEquals(
                ContractChangeKind.ADD_REQUEST_FIELD,
                ContractChangeKindMapping.allowedChangeKind(
                        DriftType.UNEXPECTED_REQUEST_FIELD
                )
        );

        assertEquals(
                ContractChangeKind.RELAX_REQUEST_REQUIRED_FIELD,
                ContractChangeKindMapping.allowedChangeKind(
                        DriftType.MISSING_REQUIRED_REQUEST_FIELD
                )
        );

        assertEquals(
                ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE,
                ContractChangeKindMapping.allowedChangeKind(
                        DriftType.REQUEST_TYPE_MISMATCH
                )
        );

        assertEquals(
                ContractChangeKind.ADD_REQUEST_ENUM_VALUE,
                ContractChangeKindMapping.allowedChangeKind(
                        DriftType.REQUEST_ENUM_MISMATCH
                )
        );

        assertEquals(
                ContractChangeKind.ADD_RESPONSE_STATUS,
                ContractChangeKindMapping.allowedChangeKind(
                        DriftType.UNDOCUMENTED_RESPONSE_STATUS
                )
        );

        assertEquals(
                ContractChangeKind.ADD_RESPONSE_FIELD,
                ContractChangeKindMapping.allowedChangeKind(
                        DriftType.UNEXPECTED_RESPONSE_FIELD
                )
        );

        assertEquals(
                ContractChangeKind.RELAX_RESPONSE_REQUIRED_FIELD,
                ContractChangeKindMapping.allowedChangeKind(
                        DriftType.MISSING_REQUIRED_RESPONSE_FIELD
                )
        );

        assertEquals(
                ContractChangeKind.CHANGE_RESPONSE_FIELD_TYPE,
                ContractChangeKindMapping.allowedChangeKind(
                        DriftType.RESPONSE_TYPE_MISMATCH
                )
        );

        assertEquals(
                ContractChangeKind.ADD_RESPONSE_ENUM_VALUE,
                ContractChangeKindMapping.allowedChangeKind(
                        DriftType.RESPONSE_ENUM_MISMATCH
                )
        );

        assertEquals(
                10,
                ContractChangeKindMapping.mappings().size()
        );
    }

    @Test
    void acceptsRepresentativeCorrectMappings() {
        assertTrue(
                ContractChangeKindMapping.isAllowed(
                        DriftType.UNDOCUMENTED_OPERATION,
                        ContractChangeKind.ADD_OPERATION
                )
        );

        assertTrue(
                ContractChangeKindMapping.isAllowed(
                        DriftType.REQUEST_TYPE_MISMATCH,
                        ContractChangeKind.CHANGE_REQUEST_FIELD_TYPE
                )
        );

        assertTrue(
                ContractChangeKindMapping.isAllowed(
                        DriftType.RESPONSE_ENUM_MISMATCH,
                        ContractChangeKind.ADD_RESPONSE_ENUM_VALUE
                )
        );
    }

    @Test
    void rejectsRepresentativeCrossMappingsAndNulls() {
        assertFalse(
                ContractChangeKindMapping.isAllowed(
                        DriftType.UNEXPECTED_REQUEST_FIELD,
                        ContractChangeKind.ADD_RESPONSE_FIELD
                )
        );

        assertFalse(
                ContractChangeKindMapping.isAllowed(
                        DriftType.MISSING_REQUIRED_RESPONSE_FIELD,
                        ContractChangeKind.RELAX_REQUEST_REQUIRED_FIELD
                )
        );

        assertFalse(
                ContractChangeKindMapping.isAllowed(
                        DriftType.UNDOCUMENTED_RESPONSE_STATUS,
                        ContractChangeKind.ADD_OPERATION
                )
        );

        assertFalse(
                ContractChangeKindMapping.isAllowed(
                        null,
                        ContractChangeKind.ADD_OPERATION
                )
        );

        assertFalse(
                ContractChangeKindMapping.isAllowed(
                        DriftType.UNDOCUMENTED_OPERATION,
                        null
                )
        );
    }
}