package com.ildcvf.llm.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProposalAttemptHistoryControllerTest {
    private final ProposalAttemptHistoryService history = mock(ProposalAttemptHistoryService.class);
    private final ProposalAttemptHistoryController controller = new ProposalAttemptHistoryController(history);

    @Test void paginationAndFiltersAreForwarded() {
        when(history.list(1, 10, "traffic-1", "finding-1", ProposalAttemptStatus.VALIDATED))
            .thenReturn(new PageImpl<>(List.of()));
        var page = controller.list(1, 10, "traffic-1", "finding-1", "VALIDATED");
        assertThat(page.getContent()).isEmpty();
        verify(history).list(1, 10, "traffic-1", "finding-1", ProposalAttemptStatus.VALIDATED);
    }
    @Test void invalidPagingAndStatusAreRejected() {
        assertThatThrownBy(() -> controller.list(-1,20,null,null,null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.list(0,101,null,null,null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.list(0,20,null,null,"UNKNOWN")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(history);
    }
    @Test void unknownAndInvalidIdsAreHandled() {
        UUID id=UUID.randomUUID();
        when(history.detail(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.detail(id.toString())).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
        assertThatThrownBy(() -> controller.detail("not-a-uuid")).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400");
    }
    @Test void onlyValidatedAttemptsAreReviewEligible() {
        var validated=new ProposalAttemptEntity(UUID.randomUUID(),"event","finding",java.time.Instant.now());
        validated.status=ProposalAttemptStatus.VALIDATED;
        assertThat(ProposalAttemptHistoryDto.from(validated).reviewEligible()).isTrue();
        validated.status=ProposalAttemptStatus.REJECTED;
        assertThat(ProposalAttemptHistoryDto.from(validated).reviewEligible()).isFalse();
        validated.status=ProposalAttemptStatus.PROVIDER_FAILURE;
        assertThat(ProposalAttemptHistoryDto.from(validated).reviewEligible()).isFalse();
    }
}
