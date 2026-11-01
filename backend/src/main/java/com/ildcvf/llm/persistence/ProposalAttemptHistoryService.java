package com.ildcvf.llm.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposalAttemptHistoryService {
    private final ProposalAttemptPersistenceService attempts;
    public ProposalAttemptHistoryService(ProposalAttemptPersistenceService attempts) { this.attempts = attempts; }

    @Transactional(readOnly = true)
    public Page<ProposalAttemptHistoryDto> list(int page, int size, String eventId, String findingId, ProposalAttemptStatus status) {
        return attempts.search(page, size, eventId, findingId, status).map(ProposalAttemptHistoryDto::from);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<ProposalAttemptHistoryDto> detail(UUID id) {
        return attempts.find(id).map(ProposalAttemptHistoryDto::from);
    }
}
