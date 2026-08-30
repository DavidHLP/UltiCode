package com.ulticode.submission.dubbo.provider;

import com.ulticode.submission.api.dto.SubmissionUserReferenceCountDTO;
import com.ulticode.submission.api.service.SubmissionReconciliationReadPort;
import com.ulticode.modules.submission.mapper.SubmissionReconciliationReadMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionReconciliationReadProviderTest {

    @Mock
    private SubmissionReconciliationReadMapper mapper;

    @Test
    void forwardsFullAndIncrementalBoundedQueries() {
        SubmissionReconciliationReadProvider provider =
                new SubmissionReconciliationReadProvider(mapper);
        List<SubmissionUserReferenceCountDTO> facts = List.of(
                new SubmissionUserReferenceCountDTO("user-1", 2),
                new SubmissionUserReferenceCountDTO("user-2", 1));
        LocalDateTime since = LocalDateTime.of(2026, 8, 30, 0, 0);
        when(mapper.findUserReferenceCounts("", since, SubmissionReconciliationReadPort.MAX_PAGE_SIZE))
                .thenReturn(facts);

        assertThat(provider.findUserReferenceCounts(
                "", since, SubmissionReconciliationReadPort.MAX_PAGE_SIZE))
                .containsExactlyElementsOf(facts);
        verify(mapper).findUserReferenceCounts(
                "", since, SubmissionReconciliationReadPort.MAX_PAGE_SIZE);
    }

    @Test
    void rejectsInvalidPageAndUnorderedFacts() {
        SubmissionReconciliationReadProvider provider =
                new SubmissionReconciliationReadProvider(mapper);

        assertThatThrownBy(() -> provider.findUserReferenceCounts(
                null, null, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.findUserReferenceCounts(
                " ", null, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> provider.findUserReferenceCounts(
                "", null, SubmissionReconciliationReadPort.MAX_PAGE_SIZE + 1))
                .isInstanceOf(IllegalArgumentException.class);

        when(mapper.findUserReferenceCounts("", null, 10)).thenReturn(List.of(
                new SubmissionUserReferenceCountDTO("user-2", 1),
                new SubmissionUserReferenceCountDTO("user-1", 1)));
        assertThatThrownBy(() -> provider.findUserReferenceCounts("", null, 10))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsUnavailableAndOversizedMapperPages() {
        SubmissionReconciliationReadProvider provider =
                new SubmissionReconciliationReadProvider(mapper);

        when(mapper.findUserReferenceCounts("", null, 10)).thenReturn(null);
        assertThatThrownBy(() -> provider.findUserReferenceCounts("", null, 10))
                .isInstanceOf(IllegalStateException.class);

        List<SubmissionUserReferenceCountDTO> oversized = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            oversized.add(new SubmissionUserReferenceCountDTO("user-" + i, 1L));
        }
        when(mapper.findUserReferenceCounts("", null, 10)).thenReturn(oversized);
        assertThatThrownBy(() -> provider.findUserReferenceCounts("", null, 10))
                .isInstanceOf(IllegalStateException.class);
    }
}
