package com.ulticode.submission.provider;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.stats.SubmissionPerformanceStats;
import com.ulticode.submission.api.dto.SubmissionListItemVO;
import com.ulticode.submission.api.dto.SubmissionQueryDTO;
import com.ulticode.submission.dubbo.provider.SubmissionUserQueryProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the code-review fixes on the user query provider: empty
 * pages must skip the remote facts RPC, and the raw pageSize from the wire
 * must be capped by the platform pagination rule.
 */
@DisplayName("SubmissionUserQueryProvider empty-page and page-size guards")
class SubmissionUserQueryProviderTest {

    private SubmissionMapper submissionMapper;
    private ProblemFactsPort problemFactsPort;
    private SubmissionUserQueryProvider provider;

    @BeforeEach
    void setUp() {
        submissionMapper = mock(SubmissionMapper.class);
        problemFactsPort = mock(ProblemFactsPort.class);
        provider = new SubmissionUserQueryProvider(
                mock(SubmissionProjection.class),
                submissionMapper,
                mock(SubmissionPerformanceStats.class),
                problemFactsPort);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubEmptyPage() {
        when(submissionMapper.findByProblemId(any(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(2));
        when(submissionMapper.findByUserId(any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    @DisplayName("findByProblemId skips the facts RPC when the DB page is empty")
    void findByProblemId_emptyPageSkipsFactsRpc() {
        stubEmptyPage();

        PageResult<SubmissionListItemVO> result =
                provider.findByProblemId(101L, "user-1", new SubmissionQueryDTO());

        assertThat(result.getItems()).isEmpty();
        verify(problemFactsPort, never()).findDisplayFactsBatch(anyCollection());
    }

    @Test
    @DisplayName("findByUserId skips the facts RPC when the DB page is empty")
    void findByUserId_emptyPageSkipsFactsRpc() {
        stubEmptyPage();

        var result = provider.findByUserId("user-1", new SubmissionQueryDTO());

        assertThat(result.getItems()).isEmpty();
        verify(problemFactsPort, never()).findDisplayFactsBatch(anyCollection());
    }

    @Test
    @DisplayName("an oversized pageSize is capped at the platform maximum")
    void oversizedPageSizeIsCapped() {
        stubEmptyPage();
        SubmissionQueryDTO query = new SubmissionQueryDTO();
        query.setPageSize(100_000);

        PageResult<SubmissionListItemVO> result =
                provider.findByProblemId(101L, "user-1", query);

        assertThat(result.getPageSize())
                .isEqualTo(PaginationRequest.MAX_PAGE_SIZE);
        verify(submissionMapper).findByProblemId(any(), any(),
                argThat((Page<Submission> page) -> page.getSize() == PaginationRequest.MAX_PAGE_SIZE));
        verify(problemFactsPort, never()).findDisplayFactsBatch(anyCollection());
    }
}
