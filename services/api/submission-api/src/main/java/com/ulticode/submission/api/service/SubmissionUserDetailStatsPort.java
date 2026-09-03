package com.ulticode.submission.api.service;

import com.ulticode.common.rpc.RpcResult;
import com.ulticode.submission.api.dto.SubmissionUserDetailStatsSnapshotDTO;

/**
 * Submission-owned snapshot contract for the Admin user-detail read.
 *
 * <p>One call returns all Submission-owned metrics required by the detail
 * surface. A failed owner read is carried by {@link RpcResult#success()} and
 * {@link RpcResult#error()}, so it cannot be confused with a valid all-zero
 * snapshot.
 */
public interface SubmissionUserDetailStatsPort {

    /**
     * Load one user's Submission-owned detail statistics.
     *
     * @param userId non-blank account identifier
     * @return successful snapshot or an explicit RPC failure envelope
     */
    RpcResult<SubmissionUserDetailStatsSnapshotDTO> getUserDetailStats(String userId);
}
