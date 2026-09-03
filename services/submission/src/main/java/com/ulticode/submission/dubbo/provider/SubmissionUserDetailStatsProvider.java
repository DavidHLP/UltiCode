package com.ulticode.submission.dubbo.provider;

import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.submission.api.dto.SubmissionUserDetailStatsSnapshotDTO;
import com.ulticode.submission.api.error.SubmissionErrorCode;
import com.ulticode.submission.api.service.SubmissionUserDetailStatsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/** Provides one Submission-owned aggregate for an Admin user detail. */
@Slf4j
@DubboService(group = "backend-submission", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionUserDetailStatsProvider implements SubmissionUserDetailStatsPort {

    private static final String DEFAULT_TRACE_ID = "t-system";

    private final SubmissionMapper submissionMapper;

    @Override
    public RpcResult<SubmissionUserDetailStatsSnapshotDTO> getUserDetailStats(String userId) {
        if (userId == null || userId.isBlank()) {
            return RpcResult.failure(SubmissionErrorCode.INVALID_USER_ID, DEFAULT_TRACE_ID);
        }

        try {
            SubmissionUserDetailStatsSnapshotDTO snapshot =
                    submissionMapper.findUserDetailStatsByUserId(userId);
            if (snapshot == null) {
                log.warn("Submission user-detail stats query returned no snapshot");
                return RpcResult.failure(
                        SubmissionErrorCode.UNEXPECTED_SUBMISSION_STATE, DEFAULT_TRACE_ID);
            }
            return RpcResult.success(snapshot, DEFAULT_TRACE_ID);
        } catch (RuntimeException exception) {
            log.error("Submission user-detail stats query failed", exception);
            return RpcResult.failure(
                    SubmissionErrorCode.UNEXPECTED_SUBMISSION_STATE, DEFAULT_TRACE_ID);
        }
    }
}
