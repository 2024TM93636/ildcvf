package com.ildcvf.experiment.llm.groq;

public final class GroqTransportException extends Exception {

    public enum Kind {
        TIMEOUT,
        TRANSPORT_FAILURE
    }

    private final Kind kind;

    public GroqTransportException(
            Kind kind,
            String message
    ) {
        super(message);
        this.kind = kind;
    }

    public GroqTransportException(
            Kind kind,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}