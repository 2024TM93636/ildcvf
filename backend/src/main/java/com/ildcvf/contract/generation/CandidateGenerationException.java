package com.ildcvf.contract.generation;

public class CandidateGenerationException extends RuntimeException {
    private final String code;
    public CandidateGenerationException(String code, String message) { super(message); this.code = code; }
    public CandidateGenerationException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
    public String code() { return code; }
}
