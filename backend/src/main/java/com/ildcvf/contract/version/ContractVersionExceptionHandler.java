package com.ildcvf.contract.version;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;

@RestControllerAdvice(assignableTypes = ContractVersionController.class)
public class ContractVersionExceptionHandler {

    public record ApiError(
            String code,
            String message,
            Instant timestamp
    ) {}

    private ResponseEntity<ApiError> error(
            HttpStatus status,
            String code,
            String message) {

        return ResponseEntity.status(status).body(
                new ApiError(code, message, Instant.now())
        );
    }

    @ExceptionHandler(ContractCandidateCreationException.class)
    public ResponseEntity<ApiError> creationFailure(
            ContractCandidateCreationException ex) {

        return switch (ex.code()) {

            case PROPOSAL_NOT_FOUND ->
                    error(HttpStatus.NOT_FOUND,
                            ex.code().name(),
                            "Proposal attempt not found");

            case PROPOSAL_NOT_VALIDATED,
                 REVIEW_MISSING,
                 REVIEW_REJECTED,
                 REVIEW_MISMATCH,
                 VERSION_EXISTS,
                 PERSISTENCE_CONFLICT ->
                    error(HttpStatus.CONFLICT,
                            ex.code().name(),
                            "Contract candidate creation is not permitted");

            case UNSUPPORTED_PROPOSAL,
                 GENERATED_DOCUMENT_INVALID ->
                    error(HttpStatus.UNPROCESSABLE_ENTITY,
                            ex.code().name(),
                            "Contract candidate cannot be generated");

            case INVALID_PROPOSAL_JSON ->
                    error(HttpStatus.UNPROCESSABLE_ENTITY,
                            ex.code().name(),
                            "Stored proposal cannot be processed");
        };
    }

    @ExceptionHandler(ContractVersionNotFoundException.class)
    public ResponseEntity<ApiError> notFound(
            ContractVersionNotFoundException ex) {

        return error(HttpStatus.NOT_FOUND,
                "VERSION_NOT_FOUND",
                "Contract version not found");
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentNotValidException.class
    })
    public ResponseEntity<ApiError> badRequest(Exception ex) {

        return error(HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Invalid request parameters or body");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {

        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "An unexpected server error occurred");
    }
}
