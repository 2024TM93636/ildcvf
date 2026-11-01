package com.ildcvf.contract.version;

import com.ildcvf.llm.persistence.ProposalAttemptEntity;
import com.ildcvf.llm.persistence.ProposalAttemptStatus;
import com.ildcvf.llm.review.ReviewDecisionEntity;
import com.ildcvf.llm.review.ReviewDecision;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class ContractVersionPersistenceService {
    private final ContractVersionRepository versions;
    private final EntityManager em;
    private final Clock clock;

    public ContractVersionPersistenceService(ContractVersionRepository versions, EntityManager em) {
        this.versions = versions;
        this.em = em;
        this.clock = Clock.systemUTC();
    }

    /**
     * Persists a candidate ALREADY validated by the caller. No OpenAPI parsing or
     * validation is performed here. M15.2 must enforce that precondition.
     * Locks the parent attempt to serialize competing version creation requests.
     */
    @Transactional
    public ContractVersionRecord saveValidated(ValidatedContractCandidate input) {
        if (input == null) throw new IllegalArgumentException("Candidate is required");
        String baseline = required(input.baselineIdentifier(), 255, "baselineIdentifier");
        String baselineSha = sha(input.baselineSha256(), "baselineSha256");
        String documentSha = sha(input.documentSha256(), "documentSha256");
        String yaml = input.openapiYaml();
        if (yaml == null || yaml.isBlank()) throw new IllegalArgumentException("openapiYaml must be nonempty");
        if (!sha256(yaml).equals(documentSha))
            throw new IllegalArgumentException("documentSha256 does not match exact YAML bytes (UTF-8)");
        UUID attemptId = input.proposalAttemptId();
        UUID reviewId = input.reviewDecisionId();
        if (attemptId == null || reviewId == null)
            throw new IllegalArgumentException("Proposal attempt and review decision are required");

        ProposalAttemptEntity attempt = em.find(ProposalAttemptEntity.class, attemptId, LockModeType.PESSIMISTIC_WRITE);
        if (attempt == null) throw new IllegalArgumentException("Proposal attempt does not exist");
        if (attempt.status != ProposalAttemptStatus.VALIDATED)
            throw new IllegalStateException("Only VALIDATED proposal attempts may be versioned");
        ReviewDecisionEntity review = em.find(ReviewDecisionEntity.class, reviewId);
        if (review == null) throw new IllegalArgumentException("Review decision does not exist");
        if (!attemptId.equals(review.proposalAttemptId))
            throw new IllegalArgumentException("Review decision belongs to a different proposal attempt");
        if (review.decision != ReviewDecision.APPROVED)
            throw new IllegalStateException("Only APPROVED review decisions may be versioned");
        if (versions.existsByProposalAttemptId(attemptId) || versions.existsByReviewDecisionId(reviewId))
            throw new IllegalStateException("A contract version already exists for this proposal or review");

        ContractVersionEntity entity = new ContractVersionEntity(UUID.randomUUID(), baseline,
                baselineSha, attemptId, reviewId, yaml, documentSha, clock.instant());
        versions.saveAndFlush(entity); // force DB uniqueness check inside transaction
        return ContractVersionRecord.from(entity);
    }

    @Transactional(readOnly = true)
    public Optional<ContractVersionRecord> find(UUID id) {
        if (id == null) throw new IllegalArgumentException("id is required");
        return versions.findById(id).map(ContractVersionRecord::from);
    }

    @Transactional(readOnly = true)
    public Optional<ContractVersionRecord> findByProposalAttempt(UUID attemptId) {
        if (attemptId == null) throw new IllegalArgumentException("attemptId is required");
        return versions.findByProposalAttemptId(attemptId).map(ContractVersionRecord::from);
    }

    @Transactional(readOnly = true)
    public Page<ContractVersionRecord> history(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        return versions.findAll(PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))
                .map(ContractVersionRecord::from);
    }

    private static String required(String s, int max, String field) {
        if (s == null || s.isBlank() || s.length() > max)
            throw new IllegalArgumentException(field + " must be nonblank and at most " + max + " characters");
        return s;
    }

    private static String sha(String s, String field) {
        if (s == null || !s.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException(field + " must be lowercase SHA-256 hex");
        return s;
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
