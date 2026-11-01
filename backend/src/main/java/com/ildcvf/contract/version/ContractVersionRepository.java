package com.ildcvf.contract.version;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ContractVersionRepository extends JpaRepository<ContractVersionEntity, UUID> {
    Optional<ContractVersionEntity> findByProposalAttemptId(UUID proposalAttemptId);
    boolean existsByProposalAttemptId(UUID proposalAttemptId);
    boolean existsByReviewDecisionId(UUID reviewDecisionId);
}
