package com.ildcvf.contract.version;

import com.ildcvf.contract.generation.CandidateGenerationException;
import com.ildcvf.contract.generation.ContractCandidateGenerator;
import com.ildcvf.contract.generation.GeneratedContractCandidate;
import com.ildcvf.llm.persistence.ProposalAttemptEntity;
import com.ildcvf.llm.persistence.ProposalAttemptPersistenceService;
import com.ildcvf.llm.persistence.ProposalAttemptStatus;
import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.review.ReviewDecision;
import com.ildcvf.llm.review.ReviewDecisionPersistenceService;
import com.ildcvf.llm.review.ReviewDecisionRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static com.ildcvf.contract.version.ContractCandidateCreationException.Code.*;

@Service
public class ContractCandidateCreationService {

    private final ProposalAttemptPersistenceService proposals;
    private final ReviewDecisionPersistenceService reviews;
    private final ContractCandidateGenerator generator;
    private final ContractVersionPersistenceService versions;
    private final ObjectMapper mapper;

    public ContractCandidateCreationService(
            ProposalAttemptPersistenceService proposals,
            ReviewDecisionPersistenceService reviews,
            ContractCandidateGenerator generator,
            ContractVersionPersistenceService versions,
            ObjectMapper mapper) {

        this.proposals = proposals;
        this.reviews = reviews;
        this.generator = generator;
        this.versions = versions;
        this.mapper = mapper;
    }

    /**
     * Explicitly creates one immutable candidate from an already-approved,
     * validated proposal attempt.
     *
     * This operation does not approve proposals or publish contracts.
     */
    @Transactional
    public ContractVersionRecord create(UUID proposalAttemptId) {

        if (proposalAttemptId == null) {
            throw new ContractCandidateCreationException(
                    PROPOSAL_NOT_FOUND,
                    "Proposal attempt ID is required"
            );
        }

        ProposalAttemptEntity attempt = proposals.find(proposalAttemptId)
                .orElseThrow(() -> new ContractCandidateCreationException(
                        PROPOSAL_NOT_FOUND,
                        "Proposal attempt does not exist"
                ));

        if (attempt.status != ProposalAttemptStatus.VALIDATED) {
            throw new ContractCandidateCreationException(
                    PROPOSAL_NOT_VALIDATED,
                    "Proposal attempt must be VALIDATED"
            );
        }

        ReviewDecisionRecord review = reviews.findByAttempt(proposalAttemptId)
                .orElseThrow(() -> new ContractCandidateCreationException(
                        REVIEW_MISSING,
                        "A final human-review decision is required"
                ));

        if (!proposalAttemptId.equals(review.proposalAttemptId())) {
            throw new ContractCandidateCreationException(
                    REVIEW_MISMATCH,
                    "Review decision does not belong to this proposal attempt"
            );
        }

        if (review.decision() != ReviewDecision.APPROVED) {
            throw new ContractCandidateCreationException(
                    REVIEW_REJECTED,
                    "The human-review decision is not APPROVED"
            );
        }

        if (versions.findByProposalAttempt(proposalAttemptId).isPresent()) {
            throw new ContractCandidateCreationException(
                    VERSION_EXISTS,
                    "A contract version already exists for this proposal"
            );
        }

        ContractChangeProposal proposal;

        try {
            if (attempt.proposalJson == null
                    || attempt.proposalJson.isNull()
                    || !attempt.proposalJson.isObject()) {
                throw new IllegalArgumentException(
                        "Stored proposal must be a JSON object"
                );
            }

            proposal = mapper.treeToValue(
                    attempt.proposalJson,
                    ContractChangeProposal.class
            );

            if (proposal == null) {
                throw new IllegalArgumentException(
                        "Stored proposal could not be deserialized"
                );
            }

        } catch (RuntimeException ex) {
            throw new ContractCandidateCreationException(
                    INVALID_PROPOSAL_JSON,
                    "Stored proposal JSON is invalid",
                    ex
            );
        }

        GeneratedContractCandidate generated;

        try {
            generated = generator.generate(proposal);

        } catch (CandidateGenerationException ex) {

            if ("UNSUPPORTED_CHANGE".equals(ex.code())
                    || "UNSUPPORTED_TARGET".equals(ex.code())
                    || "MISSING_PROPOSAL".equals(ex.code())) {

                throw new ContractCandidateCreationException(
                        UNSUPPORTED_PROPOSAL,
                        "Proposal is not supported by the candidate generator",
                        ex
                );
            }

            throw new ContractCandidateCreationException(
                    GENERATED_DOCUMENT_INVALID,
                    "Candidate generation or document validation failed",
                    ex
            );
        }

        if (generated == null
                || generated.validation() == null
                || !generated.validation().valid()) {

            throw new ContractCandidateCreationException(
                    GENERATED_DOCUMENT_INVALID,
                    "Generated contract did not pass validation"
            );
        }

        ValidatedContractCandidate candidate = new ValidatedContractCandidate(
                generated.baselineIdentifier(),
                generated.baselineSha256(),
                proposalAttemptId,
                review.reviewId(),
                generated.openapiYaml(),
                generated.documentSha256()
        );

        try {
            return versions.saveValidated(candidate);

        } catch (DataIntegrityViolationException
                 | ObjectOptimisticLockingFailureException ex) {

            throw new ContractCandidateCreationException(
                    PERSISTENCE_CONFLICT,
                    "Contract version could not be created because of a persistence conflict",
                    ex
            );

        } catch (IllegalStateException ex) {

            // The persistence layer rechecks eligibility and uniqueness
            // after acquiring the proposal-attempt database lock.
            throw new ContractCandidateCreationException(
                    PERSISTENCE_CONFLICT,
                    "Contract version creation failed during final eligibility checks",
                    ex
            );
        }
    }
}
