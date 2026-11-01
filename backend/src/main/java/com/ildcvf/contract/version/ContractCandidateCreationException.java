package com.ildcvf.contract.version;

/**
 * Structured application-service failure.
 *
 * HTTP status mapping belongs to the later REST API milestone.
 * Messages must remain safe for callers and must not expose
 * stored proposal JSON, credentials, or database details.
 */
public class ContractCandidateCreationException extends RuntimeException {

    public enum Code {
        PROPOSAL_NOT_FOUND,
        PROPOSAL_NOT_VALIDATED,
        REVIEW_MISSING,
        REVIEW_REJECTED,
        REVIEW_MISMATCH,
        VERSION_EXISTS,
        INVALID_PROPOSAL_JSON,
        UNSUPPORTED_PROPOSAL,
        GENERATED_DOCUMENT_INVALID,
        PERSISTENCE_CONFLICT
    }

    private final Code code;

    public ContractCandidateCreationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ContractCandidateCreationException(
            Code code,
            String message,
            Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
