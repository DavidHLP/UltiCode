package com.ulticode.modules.admin.port.adapter;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.submission.api.dto.SubmissionUserDetailStatsSnapshotDTO;
import com.ulticode.submission.api.error.SubmissionErrorCode;
import com.ulticode.submission.api.service.SubmissionUserDetailStatsPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionUserDetailStatsReadAdapterTest {

    @Mock
    private SubmissionUserDetailStatsPort submissionUserDetailStatsPort;

    @InjectMocks
    private SubmissionUserDetailStatsReadAdapter adapter;

    @Test
    void returnsSnapshotFromOneSuccessfulOwnerCall() {
        String userId = "user-1";
        SubmissionUserDetailStatsSnapshotDTO expected =
                new SubmissionUserDetailStatsSnapshotDTO(42, 17, 9);
        when(submissionUserDetailStatsPort.getUserDetailStats(userId))
                .thenReturn(RpcResult.success(expected, "t-success"));

        assertThat(adapter.loadUserDetailStats(userId)).isEqualTo(expected);

        verify(submissionUserDetailStatsPort).getUserDetailStats(userId);
    }

    @Test
    void preservesSuccessfulAllZeroSnapshot() {
        String userId = "user-without-submissions";
        SubmissionUserDetailStatsSnapshotDTO zero =
                new SubmissionUserDetailStatsSnapshotDTO(0, 0, 0);
        when(submissionUserDetailStatsPort.getUserDetailStats(userId))
                .thenReturn(RpcResult.success(zero, "t-zero"));

        SubmissionUserDetailStatsSnapshotDTO result = adapter.loadUserDetailStats(userId);

        assertThat(result).isEqualTo(zero);
        assertThat(result.submissionCount()).isZero();
        assertThat(result.acceptedProblemCount()).isZero();
        assertThat(result.streak()).isZero();
        verify(submissionUserDetailStatsPort).getUserDetailStats(userId);
    }

    @Test
    void mapsProviderFailureToTypedOwnerUnavailableInsteadOfZeroes() {
        String userId = "user-1";
        when(submissionUserDetailStatsPort.getUserDetailStats(userId))
                .thenReturn(RpcResult.failure(
                        SubmissionErrorCode.UNEXPECTED_SUBMISSION_STATE, "t-failure"));

        assertThatThrownBy(() -> adapter.loadUserDetailStats(userId))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode())
                        .isEqualTo(AdminErrorCode.OWNER_QUERY_UNAVAILABLE.code()));

        verify(submissionUserDetailStatsPort).getUserDetailStats(userId);
    }

    @Test
    void mapsTransportExceptionToTypedOwnerUnavailable() {
        String userId = "user-1";
        RuntimeException transportFailure = new IllegalStateException("provider unavailable");
        when(submissionUserDetailStatsPort.getUserDetailStats(userId))
                .thenThrow(transportFailure);

        assertThatThrownBy(() -> adapter.loadUserDetailStats(userId))
                .isInstanceOf(BusinessException.class)
                .hasCause(transportFailure);

        verify(submissionUserDetailStatsPort).getUserDetailStats(userId);
    }
}
