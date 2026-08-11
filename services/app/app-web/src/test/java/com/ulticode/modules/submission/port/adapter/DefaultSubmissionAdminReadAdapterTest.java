package com.ulticode.modules.submission.port.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.dto.SubmissionAdminQueryDTO;
import com.ulticode.modules.problem.adapter.DefaultProblemAdminReadAdapter;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultSubmissionAdminReadAdapterTest {

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private DefaultProblemAdminReadAdapter problemAdminReadAdapter;

    @Test
    void listRowsDoNotCarryTestDetails() {
        Submission submission = new Submission();
        submission.setId("sub-1");
        submission.setCode("secret source");
        Submission.TestCaseDetail detail = new Submission.TestCaseDetail();
        detail.setOutput("secret output");
        submission.setTestDetails(List.of(detail));

        when(submissionMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Submission> page = invocation.getArgument(0);
            page.setRecords(List.of(submission));
            page.setTotal(1);
            return page;
        });

        var result = new DefaultSubmissionAdminReadAdapter(
                submissionMapper, problemAdminReadAdapter, new ObjectMapper())
                .search(new SubmissionAdminQueryDTO(), 1, 10);

        assertThat(result.getItems()).singleElement()
                .satisfies(row -> {
                    assertThat(row.testDetails()).isEmpty();
                    assertThat(row.code()).isNull();
                });
    }

    @Test
    void titleSearchUsesProblemOwnerReadPort() {
        when(problemAdminReadAdapter.searchProblemIdsByTitle("two")).thenReturn(List.of(7L));
        when(submissionMapper.selectPage(any(Page.class), any())).thenAnswer(invocation -> {
            Page<Submission> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        SubmissionAdminQueryDTO query = new SubmissionAdminQueryDTO();
        query.setSearch("two");
        new DefaultSubmissionAdminReadAdapter(
                submissionMapper, problemAdminReadAdapter, new ObjectMapper())
                .search(query, 1, 10);

        verify(problemAdminReadAdapter).searchProblemIdsByTitle("two");
    }

    @Test
    void detailRowsNormalizeDistributionBins() {
        Submission submission = new Submission();
        submission.setId("sub-1");
        submission.setMemoryDistBinsMb("[1, 2]");
        submission.setRuntimeDistBinsMs(List.of(3, 4));
        when(submissionMapper.selectById("sub-1")).thenReturn(submission);

        var result = new DefaultSubmissionAdminReadAdapter(
                submissionMapper, problemAdminReadAdapter, new ObjectMapper())
                .findById("sub-1");

        assertThat(result.memoryDistBinsMb()).containsExactly(1, 2);
        assertThat(result.runtimeDistBinsMs()).containsExactly(3, 4);
    }
}
