package com.ildcvf.llm.demo;

import com.ildcvf.contract.service.ContractBaselineLoader;
import com.ildcvf.drift.domain.DriftAnalysisResult;
import com.ildcvf.drift.domain.DriftFinding;
import com.ildcvf.drift.domain.EvaluationStatus;
import com.ildcvf.drift.service.DriftAnalysisService;
import com.ildcvf.llm.evidence.GroundedEvidenceBuilder;
import com.ildcvf.llm.evidence.GroundedEvidencePackage;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.persistence.ProposalAttemptPersistenceService;
import com.ildcvf.llm.persistence.ProposalAttemptStatus;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.UUID;
import com.ildcvf.llm.validation.ContractChangeProposalValidator;
import com.ildcvf.llm.validation.ProposalValidationResult;
import com.ildcvf.traffic.repository.TrafficEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
public class LiveProposalDemoService {
    private final TrafficEventRepository repository;
    private final DriftAnalysisService driftService;
    private final ContractBaselineLoader baselineLoader;
    private final GroqDemoClient provider;
    private final ProposalAttemptPersistenceService attempts;
    private final GroundedEvidenceBuilder evidenceBuilder = new GroundedEvidenceBuilder();
    private final ContractChangeProposalValidator validator = new ContractChangeProposalValidator();

    public LiveProposalDemoService(TrafficEventRepository repository,
                                   DriftAnalysisService driftService,
                                   ContractBaselineLoader baselineLoader,
                                   GroqDemoClient provider,
                                   ProposalAttemptPersistenceService attempts) {
        this.repository = Objects.requireNonNull(repository);
        this.driftService = Objects.requireNonNull(driftService);
        this.baselineLoader = Objects.requireNonNull(baselineLoader);
        this.provider = Objects.requireNonNull(provider);
        this.attempts = Objects.requireNonNull(attempts);
    }

    public record Response(String status, String trafficEventId, String findingId,
                           String providerStatus, ContractChangeProposal proposal,
                           ProposalValidationResult validation, String message,
                           Integer providerHttpStatus, UUID attemptId) {}

    public Response generate(String eventId, String findingId) {
        if (eventId == null || eventId.isBlank() || findingId == null || findingId.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "trafficEventId and findingId are required");
        var event = repository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Persisted event not found"));
        DriftAnalysisResult analysis = driftService.analyze(event);
        if (analysis.evaluationStatus() != EvaluationStatus.EVALUATED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event is not fully evaluated");
        DriftFinding finding = analysis.findings().stream()
                .filter(f -> findingId.equals(f.findingId()) && eventId.equals(f.trafficEventId()))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Finding not found in authoritative event analysis"));
        GroundedEvidencePackage evidence = evidenceBuilder.build(analysis, finding, baselineLoader.getBaseline());
        // The start transaction commits before provider I/O. A crash leaves IN_PROGRESS visible.
        final UUID attemptId;
        try {
            attemptId = attempts.start(eventId, findingId);
        } catch (DataIntegrityViolationException ex) {
            // The partial unique index is the authoritative concurrency guard.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Proposal generation is already in progress for this event and finding");
        }

        final GroqDemoClient.Outcome outcome;
        try {
            outcome = provider.generate(evidence);
        } catch (RuntimeException ex) {
            // Never expose or persist provider exception messages: they may contain secrets.
            attempts.completeFailure(attemptId, ProposalAttemptStatus.PROVIDER_FAILURE,
                    "UNEXPECTED_PROVIDER_EXCEPTION", null, "Provider invocation failed unexpectedly.");
            return new Response("PROVIDER_FAILURE", eventId, findingId,
                    "UNEXPECTED_PROVIDER_EXCEPTION", null, null,
                    "Provider invocation failed unexpectedly.", null, attemptId);
        }
        if (outcome.proposal() == null) {
            ProposalAttemptStatus terminal = switch (outcome.status()) {
                case "TIMEOUT" -> ProposalAttemptStatus.TIMEOUT;
                case "PARSE_FAILURE" -> ProposalAttemptStatus.PARSE_FAILURE;
                default -> ProposalAttemptStatus.PROVIDER_FAILURE;
            };
            // Use a fixed, non-sensitive message. Never store rawProviderResponse.
            String safeMessage = switch (terminal) {
                case TIMEOUT -> "Provider request timed out.";
                case PARSE_FAILURE -> "Provider response or proposal could not be parsed.";
                default -> "Provider request failed.";
            };
            attempts.completeFailure(attemptId, terminal,
                    outcome.providerStatus(), outcome.httpStatus(), safeMessage);
            // Preserve the existing response message semantics; avoid raw provider diagnostics.
            return new Response(terminal.name(), eventId, findingId,
                    outcome.providerStatus(), null, null, safeMessage,
                    outcome.httpStatus(), attemptId);
        }
        final ProposalValidationResult validation;
        try {
            validation = validator.validate(evidence, outcome.proposal());
        } catch (RuntimeException ex) {
            attempts.completeFailure(attemptId, ProposalAttemptStatus.PARSE_FAILURE,
                    outcome.providerStatus(), outcome.httpStatus(),
                    "Proposal validation could not be completed.");
            return new Response("PARSE_FAILURE", eventId, findingId,
                    outcome.providerStatus(), null, null,
                    "Proposal validation could not be completed.", outcome.httpStatus(), attemptId);
        }
        if (validation.valid()) {
            attempts.completeValidated(attemptId, outcome.proposal(), validation, outcome.providerStatus(), outcome.httpStatus());
        } else {
            attempts.completeRejected(attemptId, outcome.proposal(), validation, outcome.providerStatus(), outcome.httpStatus());
        }
        return new Response(validation.valid() ? "VALIDATED" : "REJECTED",
                eventId, findingId, outcome.providerStatus(), outcome.proposal(), validation,
                null, outcome.httpStatus(), attemptId);
    }

}
