package com.ildcvf.llm.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/llm/demo/proposals")
public class ProposalAttemptHistoryController {
    private final ProposalAttemptHistoryService history;
    public ProposalAttemptHistoryController(ProposalAttemptHistoryService history) { this.history = history; }

    @GetMapping
    public Page<ProposalAttemptHistoryDto> list(
            @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) String trafficEventId,
            @RequestParam(required=false) String findingId,
            @RequestParam(required=false) String status) {
        if (page < 0 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0; size must be 1..100");
        ProposalAttemptStatus parsed = null;
        if (status != null && !status.isBlank()) {
            try { parsed = ProposalAttemptStatus.valueOf(status); }
            catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attempt status");
            }
        }
        if (trafficEventId != null && trafficEventId.isBlank()) trafficEventId = null;
        if (findingId != null && findingId.isBlank()) findingId = null;
        return history.list(page, size, trafficEventId, findingId, parsed);
    }

    @GetMapping("/{attemptId}")
    public ProposalAttemptHistoryDto detail(@PathVariable String attemptId) {
        final UUID id;
        try { id = UUID.fromString(attemptId); }
        catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attempt ID");
        }
        return history.detail(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Proposal attempt not found"));
    }
}
