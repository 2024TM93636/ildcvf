package com.ildcvf.experiment.llm.groq;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class TransportDiagnosticClassifier {

    public TransportDiagnostic classify(
            GroqTransportException exception
    ) {
        Objects.requireNonNull(
                exception,
                "exception must not be null"
        );

        Throwable classified =
                firstClassifiableThrowable(exception);

        return new TransportDiagnostic(
                exception.kind().name(),
                exception.getClass().getName(),
                exception.getCause() == null
                        ? null
                        : exception.getCause()
                        .getClass()
                        .getName(),
                classifyCode(
                        exception,
                        classified
                )
        );
    }

    private Throwable firstClassifiableThrowable(
            GroqTransportException exception
    ) {
        Set<Throwable> visited = new HashSet<>();

        Throwable current = exception;

        while (current != null
                && visited.add(current)) {

            if (current instanceof UnknownHostException
                    || current instanceof SSLException
                    || current instanceof ConnectException
                    || current instanceof HttpTimeoutException
                    || current instanceof InterruptedException
                    || current instanceof SocketException
                    || current instanceof IOException) {
                return current;
            }

            current = current.getCause();
        }

        return null;
    }

    private TransportDiagnosticCode classifyCode(
            GroqTransportException exception,
            Throwable classified
    ) {
        /*
         * Preserve the existing typed timeout boundary even if
         * a synthetic/future transport exception lacks a cause.
         */
        if (exception.kind()
                == GroqTransportException.Kind.TIMEOUT) {
            return TransportDiagnosticCode.REQUEST_TIMEOUT;
        }

        if (classified instanceof UnknownHostException) {
            return TransportDiagnosticCode.DNS_FAILURE;
        }

        if (classified instanceof SSLException) {
            return TransportDiagnosticCode.TLS_FAILURE;
        }

        if (classified instanceof ConnectException) {
            return TransportDiagnosticCode.CONNECT_FAILURE;
        }

        if (classified instanceof HttpTimeoutException) {
            return TransportDiagnosticCode.REQUEST_TIMEOUT;
        }

        if (classified instanceof InterruptedException) {
            return TransportDiagnosticCode.INTERRUPTED;
        }

        if (classified instanceof SocketException) {
            return TransportDiagnosticCode.SOCKET_IO_FAILURE;
        }

        if (classified instanceof IOException) {
            return TransportDiagnosticCode.OTHER_IO_FAILURE;
        }

        return TransportDiagnosticCode.OTHER_TRANSPORT_FAILURE;
    }
}