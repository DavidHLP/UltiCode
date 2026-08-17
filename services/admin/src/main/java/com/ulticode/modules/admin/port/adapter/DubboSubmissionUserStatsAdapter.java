package com.ulticode.modules.admin.port.adapter;

import com.ulticode.common.dto.DifficultyCountDTO;
import com.ulticode.submission.api.dto.SubmissionDateCountDTO;
import com.ulticode.submission.api.service.SubmissionUserStatsPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Admin consumer adapter for App-owned per-user submission statistics.
 */
@Primary
@Component
public class DubboSubmissionUserStatsAdapter implements SubmissionUserStatsPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private SubmissionUserStatsPort submissionUserStatsPort;

    @Override
    public Long countAcceptedProblemsByUserId(String userId) {
        return submissionUserStatsPort.countAcceptedProblemsByUserId(userId);
    }

    @Override
    public Long countByUserId(String userId) {
        return submissionUserStatsPort.countByUserId(userId);
    }

    @Override
    public Long countTotalSubmissionsByUserId(String userId) {
        return submissionUserStatsPort.countTotalSubmissionsByUserId(userId);
    }

    @Override
    public Double calculateAcceptanceRateByUserId(String userId) {
        return submissionUserStatsPort.calculateAcceptanceRateByUserId(userId);
    }

    @Override
    public Integer findGlobalRankByUserId(String userId) {
        return submissionUserStatsPort.findGlobalRankByUserId(userId);
    }

    @Override
    public List<DifficultyCountDTO> countAcceptedProblemsByDifficulty(String userId) {
        return submissionUserStatsPort.countAcceptedProblemsByDifficulty(userId);
    }

    @Override
    public List<SubmissionDateCountDTO> findSubmissionCountsByDate(String userId, Integer year) {
        return submissionUserStatsPort.findSubmissionCountsByDate(userId, year);
    }
}
