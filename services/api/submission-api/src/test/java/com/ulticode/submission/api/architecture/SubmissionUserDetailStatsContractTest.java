package com.ulticode.submission.api.architecture;

import com.ulticode.common.rpc.RpcResult;
import com.ulticode.submission.api.dto.SubmissionUserDetailStatsSnapshotDTO;
import com.ulticode.submission.api.error.SubmissionErrorCode;
import com.ulticode.submission.api.service.SubmissionUserDetailStatsPort;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SubmissionUserDetailStatsContractTest {

    @Test
    void serviceExposesOneRpcResultMethodWithStringUserId() throws NoSuchMethodException {
        Method method = SubmissionUserDetailStatsPort.class.getDeclaredMethod(
                "getUserDetailStats", String.class);

        assertThat(SubmissionUserDetailStatsPort.class.getDeclaredMethods()).hasSize(1);
        assertThat(method.getReturnType()).isEqualTo(RpcResult.class);
        assertThat(method.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void snapshotContainsOnlySubmissionOwnedMetrics() {
        assertThat(SubmissionUserDetailStatsSnapshotDTO.class.isRecord()).isTrue();
        assertThat(Arrays.stream(SubmissionUserDetailStatsSnapshotDTO.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .containsExactly("submissionCount", "acceptedProblemCount", "streak");
        assertThat(Arrays.stream(SubmissionUserDetailStatsSnapshotDTO.class.getRecordComponents())
                .map(component -> component.getType().getName())
                .toList())
                .containsExactly("long", "long", "int");
        assertThat(Serializable.class.isAssignableFrom(SubmissionUserDetailStatsSnapshotDTO.class))
                .isTrue();
    }

    @Test
    void zeroSnapshotIsSuccessWhileFailureEnvelopeHasNoData() {
        SubmissionUserDetailStatsSnapshotDTO zero =
                new SubmissionUserDetailStatsSnapshotDTO(0, 0, 0);
        RpcResult<SubmissionUserDetailStatsSnapshotDTO> success = RpcResult.success(zero, "t-zero");
        RpcResult<SubmissionUserDetailStatsSnapshotDTO> failure = RpcResult.failure(
                SubmissionErrorCode.UNEXPECTED_SUBMISSION_STATE, "t-failure");

        assertThat(success.success()).isTrue();
        assertThat(success.data()).isEqualTo(zero);
        assertThat(failure.success()).isFalse();
        assertThat(failure.data()).isNull();
        assertThat(failure.error().namespace()).isEqualTo(SubmissionErrorCode.NAMESPACE);
    }

    @Test
    void snapshotRejectsNegativeProviderValues() {
        assertThatThrownBy(() -> new SubmissionUserDetailStatsSnapshotDTO(-1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SubmissionUserDetailStatsSnapshotDTO(0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SubmissionUserDetailStatsSnapshotDTO(0, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
