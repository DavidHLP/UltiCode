package com.ulticode.modules.admin.port.adapter;

import com.ulticode.admin.error.AdminReadContract;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.port.AdminSubmissionUserDetailStatsReadPort;
import com.ulticode.submission.api.dto.SubmissionUserDetailStatsSnapshotDTO;
import com.ulticode.submission.api.service.SubmissionUserDetailStatsPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Adapts the Submission-owned user-detail snapshot to the Admin read port. */
@Primary
@Component
public class SubmissionUserDetailStatsReadAdapter
        implements AdminSubmissionUserDetailStatsReadPort {

    @DubboReference(group = "backend-submission", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionUserDetailStatsPort submissionUserDetailStatsPort;

    @Override
    public SubmissionUserDetailStatsSnapshotDTO loadUserDetailStats(String userId) {
        RpcResult<SubmissionUserDetailStatsSnapshotDTO> result;
        try {
            result = submissionUserDetailStatsPort.getUserDetailStats(userId);
        } catch (RuntimeException exception) {
            throw AdminReadContract.ownerUnavailable("Submission", exception);
        }
        if (result == null || !result.success() || result.data() == null) {
            throw AdminReadContract.ownerUnavailable("Submission");
        }
        return result.data();
    }
}
