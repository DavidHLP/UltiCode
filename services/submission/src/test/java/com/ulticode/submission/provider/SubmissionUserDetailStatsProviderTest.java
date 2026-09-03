package com.ulticode.submission.provider;

import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.submission.api.dto.SubmissionUserDetailStatsSnapshotDTO;
import com.ulticode.submission.api.error.SubmissionErrorCode;
import com.ulticode.submission.api.service.SubmissionUserDetailStatsPort;
import com.ulticode.submission.dubbo.provider.SubmissionUserDetailStatsProvider;
import org.apache.dubbo.config.annotation.DubboService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmissionUserDetailStatsProviderTest {

    @Mock
    private SubmissionMapper submissionMapper;

    @Test
    void publishesOneVersionedSubmissionOwnerContract() {
        DubboService service = SubmissionUserDetailStatsProvider.class
                .getAnnotation(DubboService.class);

        assertThat(SubmissionUserDetailStatsProvider.class.getInterfaces())
                .containsExactly(SubmissionUserDetailStatsPort.class);
        assertThat(service).isNotNull();
        assertThat(service.group()).isEqualTo("backend-submission");
        assertThat(service.version()).isEqualTo("1.0.0");
    }

    @Test
    void aggregatesAllMetricsWithOneMapperCall() {
        String userId = "user-1";
        SubmissionUserDetailStatsSnapshotDTO expected =
                new SubmissionUserDetailStatsSnapshotDTO(42, 17, 9);
        when(submissionMapper.findUserDetailStatsByUserId(userId)).thenReturn(expected);
        SubmissionUserDetailStatsProvider provider = new SubmissionUserDetailStatsProvider(submissionMapper);

        RpcResult<SubmissionUserDetailStatsSnapshotDTO> result = provider.getUserDetailStats(userId);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(expected);
        assertThat(result.error()).isNull();
        verify(submissionMapper).findUserDetailStatsByUserId(userId);
    }

    @Test
    void preservesRealZeroValuesAsSuccessfulData() {
        String userId = "user-without-submissions";
        SubmissionUserDetailStatsSnapshotDTO expected =
                new SubmissionUserDetailStatsSnapshotDTO(0, 0, 0);
        when(submissionMapper.findUserDetailStatsByUserId(userId)).thenReturn(expected);
        SubmissionUserDetailStatsProvider provider = new SubmissionUserDetailStatsProvider(submissionMapper);

        RpcResult<SubmissionUserDetailStatsSnapshotDTO> result = provider.getUserDetailStats(userId);

        assertThat(result.success()).isTrue();
        assertThat(result.data()).isEqualTo(expected);
        assertThat(result.data().submissionCount()).isZero();
        assertThat(result.data().acceptedProblemCount()).isZero();
        assertThat(result.data().streak()).isZero();
        verify(submissionMapper).findUserDetailStatsByUserId(userId);
    }

    @Test
    void rejectsBlankUserIdsBeforeQuerying() {
        SubmissionUserDetailStatsProvider provider = new SubmissionUserDetailStatsProvider(submissionMapper);

        RpcResult<SubmissionUserDetailStatsSnapshotDTO> result = provider.getUserDetailStats("  ");

        assertThat(result.success()).isFalse();
        assertThat(result.data()).isNull();
        assertThat(result.error().namespace()).isEqualTo(SubmissionErrorCode.NAMESPACE);
        assertThat(result.error().code()).isEqualTo(SubmissionErrorCode.INVALID_USER_ID.code());
        verifyNoInteractions(submissionMapper);
    }

    @Test
    void turnsMissingAggregateIntoExplicitFailure() {
        String userId = "user-1";
        when(submissionMapper.findUserDetailStatsByUserId(userId)).thenReturn(null);
        SubmissionUserDetailStatsProvider provider = new SubmissionUserDetailStatsProvider(submissionMapper);

        RpcResult<SubmissionUserDetailStatsSnapshotDTO> result = provider.getUserDetailStats(userId);

        assertThat(result.success()).isFalse();
        assertThat(result.data()).isNull();
        assertThat(result.error().code())
                .isEqualTo(SubmissionErrorCode.UNEXPECTED_SUBMISSION_STATE.code());
        verify(submissionMapper).findUserDetailStatsByUserId(userId);
    }

    @Test
    void turnsMapperFailureIntoExplicitFailure() {
        String userId = "user-1";
        when(submissionMapper.findUserDetailStatsByUserId(userId))
                .thenThrow(new IllegalStateException("database unavailable"));
        SubmissionUserDetailStatsProvider provider = new SubmissionUserDetailStatsProvider(submissionMapper);

        RpcResult<SubmissionUserDetailStatsSnapshotDTO> result = provider.getUserDetailStats(userId);

        assertThat(result.success()).isFalse();
        assertThat(result.data()).isNull();
        assertThat(result.error().namespace()).isEqualTo(SubmissionErrorCode.NAMESPACE);
        assertThat(result.error().code())
                .isEqualTo(SubmissionErrorCode.UNEXPECTED_SUBMISSION_STATE.code());
        verify(submissionMapper).findUserDetailStatsByUserId(userId);
    }
}
