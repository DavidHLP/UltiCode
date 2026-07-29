package com.ulticode.modules.submission.service;

import com.ulticode.modules.submission.dto.BatchRejudgeResponse;
import com.ulticode.modules.submission.dto.RejudgeResult;
import com.ulticode.modules.submission.port.SubmissionAdministrationWritePort;
import com.ulticode.modules.submission.service.impl.SubmissionAdministrationDomainServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmissionAdministrationDomainServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-29T10:00:00Z");

    @Mock
    private SubmissionAdministrationWritePort writePort;

    private SubmissionAdministrationDomainServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SubmissionAdministrationDomainServiceImpl(writePort);
    }

    @Nested
    @DisplayName("rejudge")
    class Rejudge {

        @Test
        @DisplayName("successful rejudge delegates to writePort.rejudgeSubmission")
        void success() {
            RejudgeResult expected = new RejudgeResult();
            expected.setSubmissionId("sub-1");
            expected.setSuccess(true);
            expected.setOldStatus("ACCEPTED");
            expected.setNewStatus("PENDING");
            expected.setRejudgedAt(FIXED_NOW);
            expected.setRetryCount(1);

            when(writePort.rejudgeSubmission("sub-1", true)).thenReturn(expected);

            RejudgeResult actual = service.rejudge("sub-1", true);

            assertThat(actual).isSameAs(expected);
            verify(writePort).rejudgeSubmission("sub-1", true);
        }
    }

    @Nested
    @DisplayName("batchRejudge")
    class BatchRejudge {

        @Test
        @DisplayName("batchRejudge delegates to writePort.batchRejudgeSubmissions including partial failure")
        void partialFailure() {
            RejudgeResult r1 = new RejudgeResult();
            r1.setSubmissionId("sub-1");
            r1.setSuccess(true);

            RejudgeResult r2 = new RejudgeResult();
            r2.setSubmissionId("sub-2");
            r2.setSuccess(false);
            r2.setError("Submission not found");

            BatchRejudgeResponse expected = new BatchRejudgeResponse();
            expected.setTotal(2);
            expected.setSuccessful(1);
            expected.setFailed(1);
            expected.setResults(List.of(r1, r2));

            when(writePort.batchRejudgeSubmissions(List.of("sub-1", "sub-2"), true)).thenReturn(expected);

            BatchRejudgeResponse actual = service.batchRejudge(List.of("sub-1", "sub-2"), true);

            assertThat(actual).isSameAs(expected);
            verify(writePort).batchRejudgeSubmissions(List.of("sub-1", "sub-2"), true);
        }
    }
}
