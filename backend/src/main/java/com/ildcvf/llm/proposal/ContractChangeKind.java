package com.ildcvf.llm.proposal;

/**
 * Deterministic taxonomy of contract-change shapes that a future LLM may
 * propose.
 *
 * <p>A change kind being allowed for a drift type does not mean the proposed
 * contract change is automatically correct or approved. Deterministic
 * validation and later human review remain required.</p>
 */
public enum ContractChangeKind {

    ADD_OPERATION,

    ADD_REQUEST_FIELD,
    RELAX_REQUEST_REQUIRED_FIELD,
    CHANGE_REQUEST_FIELD_TYPE,
    ADD_REQUEST_ENUM_VALUE,

    ADD_RESPONSE_STATUS,
    ADD_RESPONSE_FIELD,
    RELAX_RESPONSE_REQUIRED_FIELD,
    CHANGE_RESPONSE_FIELD_TYPE,
    ADD_RESPONSE_ENUM_VALUE
}
