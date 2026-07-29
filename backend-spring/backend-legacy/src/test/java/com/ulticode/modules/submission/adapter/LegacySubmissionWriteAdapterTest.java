package com.ulticode.modules.submission.adapter;

import com.ulticode.modules.admin.service.AdminSubmissionService;
import com.ulticode.modules.submission.dto.BatchRejudgeResponse;
import com.ulticode.modules.submission.dto.RejudgeResult;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LegacySubmissionWriteAdapterTest {

    @Mock
    private SubmissionMapper submissionMapper;

    @Mock
    private AdminSubmissionService adminSubmissionService;

    private LegacySubmissionWriteAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LegacySubmissionWriteAdapter(submissionMapper, adminSubmissionService);
    }

    @Test
    @DisplayName("selectById delegates to submissionMapper")
    void selectById() {
        Submission expected = new Submission();
        expected.setId("sub-1");

        when(submissionMapper.selectById("sub-1")).thenReturn(expected);

        Submission actual = adapter.selectById("sub-1");

        assertThat(actual).isSameAs(expected);
        verify(submissionMapper).selectById("sub-1");
    }

    @Test
    @DisplayName("rejudgeSubmission delegates directly to adminSubmissionService")
    void rejudgeSubmissionMapping() {
        RejudgeResult domainRes = new RejudgeResult();
        domainRes.setSubmissionId("sub-1");
        domainRes.setSuccess(true);
        domainRes.setOldStatus("WRONG_ANSWER");
        domainRes.setNewStatus("PENDING");
        domainRes.setError(null);
        domainRes.setRejudgedAt(Instant.parse("2026-07-29T10:00:00Z"));
        domainRes.setRetryCount(2);

        when(adminSubmissionService.rejudge("sub-1", true)).thenReturn(domainRes);

        RejudgeResult actual = adapter.rejudgeSubmission("sub-1", true);

        assertThat(actual).isSameAs(domainRes);
        verify(adminSubmissionService).rejudge("sub-1", true);
    }

    @Test
    @DisplayName("batchRejudgeSubmissions delegates directly to adminSubmissionService")
    void batchRejudgeSubmissionsMapping() {
        RejudgeResult r1 = new RejudgeResult();
        r1.setSubmissionId("sub-1");
        r1.setSuccess(true);

        RejudgeResult r2 = new RejudgeResult();
        r2.setSubmissionId("sub-2");
        r2.setSuccess(false);
        r2.setError("Submission not found");

        BatchRejudgeResponse domainBatch = new BatchRejudgeResponse();
        domainBatch.setTotal(2);
        domainBatch.setSuccessful(1);
        domainBatch.setFailed(1);
        domainBatch.setResults(List.of(r1, r2));

        when(adminSubmissionService.batchRejudge(List.of("sub-1", "sub-2"), true)).thenReturn(domainBatch);

        BatchRejudgeResponse actual = adapter.batchRejudgeSubmissions(List.of("sub-1", "sub-2"), true);

        assertThat(actual).isSameAs(domainBatch);
        verify(adminSubmissionService).batchRejudge(List.of("sub-1", "sub-2"), true);
    }
}
