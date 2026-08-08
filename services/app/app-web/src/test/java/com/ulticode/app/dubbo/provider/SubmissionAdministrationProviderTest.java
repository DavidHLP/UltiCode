package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.command.ActorDelegation;
import com.ulticode.app.api.command.BatchRejudgeCommand;
import com.ulticode.app.api.command.RejudgeCommand;
import com.ulticode.app.api.dto.BatchRejudgeResultDTO;
import com.ulticode.app.api.dto.RejudgeResultDTO;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.modules.submission.dto.BatchRejudgeResponse;
import com.ulticode.modules.submission.dto.RejudgeResult;
import com.ulticode.modules.submission.service.SubmissionAdministrationDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionAdministrationProvider")
class SubmissionAdministrationProviderTest {

    @Mock private SubmissionAdministrationDomainService domainService;
    private SubmissionAdministrationProvider provider;

    @BeforeEach
    void setUp() { provider = new SubmissionAdministrationProvider(domainService); }

    private static ActorDelegation actor() {
        return new ActorDelegation("ADMIN", "admin", "admin", "test");
    }

    @Test @DisplayName("rejudge maps domain result to DTO")
    void rejudges() {
        RejudgeResult domainRes = new RejudgeResult();
        domainRes.setSubmissionId("sub-1");
        domainRes.setSuccess(true);
        domainRes.setOldStatus("WRONG_ANSWER");
        domainRes.setNewStatus("PENDING");
        domainRes.setError(null);
        domainRes.setRejudgedAt(Instant.parse("2026-07-29T10:00:00Z"));
        domainRes.setRetryCount(2);

        when(domainService.rejudge("sub-1", true)).thenReturn(domainRes);

        var cmd = new RejudgeCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(),
                actor(), TraceMetadata.EMPTY, "sub-1", true);
        RpcResult<RejudgeResultDTO> result = provider.rejudge(cmd);

        assertThat(result.success()).isTrue();
        assertThat(result.data().submissionId()).isEqualTo("sub-1");
        assertThat(result.data().newStatus()).isEqualTo("PENDING");
        assertThat(result.data().retryCount()).isEqualTo(2);
    }

    @Test @DisplayName("batchRejudge maps domain batch response to DTO")
    void batchRejudges() {
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

        when(domainService.batchRejudge(List.of("sub-1", "sub-2"), true)).thenReturn(domainBatch);

        var cmd = new BatchRejudgeCommand(
                UUID.randomUUID().toString(), IdMetadata.mint(),
                actor(), TraceMetadata.EMPTY, List.of("sub-1", "sub-2"), true);
        RpcResult<BatchRejudgeResultDTO> result = provider.batchRejudge(cmd);

        assertThat(result.success()).isTrue();
        assertThat(result.data().total()).isEqualTo(2);
        assertThat(result.data().successful()).isEqualTo(1);
        assertThat(result.data().failed()).isEqualTo(1);
        assertThat(result.data().results()).hasSize(2);
        assertThat(result.data().results().get(0).submissionId()).isEqualTo("sub-1");
    }
}
