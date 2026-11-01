package com.ildcvf.llm.persistence;

import com.ildcvf.llm.proposal.ContractChangeProposal;
import com.ildcvf.llm.validation.ProposalValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProposalAttemptPersistenceService {
    private final ProposalAttemptRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Autowired
    public ProposalAttemptPersistenceService(ProposalAttemptRepository repository,ObjectMapper mapper) {
        this(repository,mapper,Clock.systemUTC());
    }
    ProposalAttemptPersistenceService(ProposalAttemptRepository repository,ObjectMapper mapper,Clock clock) {
        this.repository=repository;this.mapper=mapper;this.clock=clock;
    }
    @Transactional
    public UUID start(String eventId,String findingId) {
        if(eventId==null||eventId.isBlank()||eventId.length()>64||findingId==null||findingId.isBlank()||findingId.length()>255)
            throw new IllegalArgumentException("Invalid source identifiers");
        UUID id=UUID.randomUUID();
        // flush forces the database's partial unique index to reject concurrent active attempts now.
        repository.saveAndFlush(new ProposalAttemptEntity(id,eventId,findingId,clock.instant()));
        return id;
    }
    @Transactional
    public void completeValidated(UUID id,ContractChangeProposal proposal,ProposalValidationResult validation) {
        if(!Objects.requireNonNull(validation).valid()) throw new IllegalArgumentException("Validation must be valid");
        completeValidated(id,proposal,validation,null,null);
    }
    @Transactional
    public void completeRejected(UUID id,ContractChangeProposal proposal,ProposalValidationResult validation) {
        if(Objects.requireNonNull(validation).valid()) throw new IllegalArgumentException("Validation must contain errors");
        completeRejected(id,proposal,validation,null,null);
    }
    @Transactional
    public void completeValidated(UUID id,ContractChangeProposal proposal,ProposalValidationResult validation,String providerStatus,Integer httpStatus) {
        if(!Objects.requireNonNull(validation).valid()) throw new IllegalArgumentException("Validation must be valid");
        finish(id,ProposalAttemptStatus.VALIDATED,providerStatus,httpStatus,Objects.requireNonNull(proposal),validation,null);
    }
    @Transactional
    public void completeRejected(UUID id,ContractChangeProposal proposal,ProposalValidationResult validation,String providerStatus,Integer httpStatus) {
        if(Objects.requireNonNull(validation).valid()) throw new IllegalArgumentException("Validation must contain errors");
        finish(id,ProposalAttemptStatus.REJECTED,providerStatus,httpStatus,Objects.requireNonNull(proposal),validation,null);
    }
    @Transactional
    public void completeFailure(UUID id,ProposalAttemptStatus status,String providerStatus,Integer httpStatus,String safeMessage) {
        if(status!=ProposalAttemptStatus.PROVIDER_FAILURE && status!=ProposalAttemptStatus.PARSE_FAILURE && status!=ProposalAttemptStatus.TIMEOUT)
            throw new IllegalArgumentException("Not a failure status");
        finish(id,status,providerStatus,httpStatus,null,null,safeMessage);
    }
    private void finish(UUID id,ProposalAttemptStatus status,String providerStatus,Integer httpStatus,ContractChangeProposal proposal,ProposalValidationResult validation,String safeMessage) {
        ProposalAttemptEntity attempt=repository.findById(id).orElseThrow();
        if(attempt.status!=ProposalAttemptStatus.IN_PROGRESS) throw new IllegalStateException("Attempt already completed");
        attempt.status=status;
        attempt.providerStatus=providerStatus;
        attempt.providerHttpStatus=httpStatus;
        attempt.proposalJson=proposal==null?null:mapper.valueToTree(proposal);
        attempt.validationJson=validation==null?null:mapper.valueToTree(validation);
        attempt.failureMessage=safeMessage==null?null:sanitize(safeMessage);
        attempt.completedAt=clock.instant();
        repository.save(attempt);
    }
    private static String sanitize(String text) {
        // Only pass pre-sanitized, non-sensitive failure descriptions here.
        String singleLine=text.replaceAll("[\r\n\t]"," ");
        return singleLine.length()>300?singleLine.substring(0,300):singleLine;
    }
    @Transactional(readOnly=true)
    public Optional<ProposalAttemptEntity> find(UUID id) { return repository.findById(id); }
    @Transactional(readOnly=true)
    public List<ProposalAttemptEntity> history(int limit) { return repository.findByOrderByCreatedAtDescIdDesc(page(limit)); }
    @Transactional(readOnly=true)
    public List<ProposalAttemptEntity> byEvent(String eventId,int limit) {
        return repository.findByTrafficEventIdOrderByCreatedAtDescIdDesc(eventId,page(limit));
    }
    @Transactional(readOnly=true)
    public List<ProposalAttemptEntity> byFinding(String eventId,String findingId,int limit) {
        return repository.findByTrafficEventIdAndFindingIdOrderByCreatedAtDescIdDesc(eventId,findingId,page(limit));
    }
    @Transactional(readOnly=true)
    public List<ProposalAttemptEntity> stale(Duration age,int limit) {
        if(age==null||age.isNegative()||age.isZero()) throw new IllegalArgumentException("age must be positive");
        return repository.findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(ProposalAttemptStatus.IN_PROGRESS,clock.instant().minus(age),page(limit));
    }
    @Transactional(readOnly=true)
    public Page<ProposalAttemptEntity> search(int page, int size, String eventId, String findingId, ProposalAttemptStatus status) {
        if(page<0 || size<1 || size>100) throw new IllegalArgumentException("Invalid page or size");
        return repository.search(eventId, findingId, status,
            PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
    }
    private static PageRequest page(int limit) {
        if(limit<1||limit>100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return PageRequest.of(0,limit);
    }
}
