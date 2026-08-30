package com.ulticode.modules.submission.port.adapter;

import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.submission.api.dto.ProblemDifficultyCompletion;
import com.ulticode.submission.api.dto.ProblemTrend;
import com.ulticode.submission.api.service.ProblemSubmissionStatsPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** App adapter for Submission-owner Problem statistics. */
@Component
@Primary
@ConditionalOnExpression("'${app.runtime.mode:dev-lite}' != 'legacy-rollback'")
public class RemoteProblemSubmissionStatsAdapter implements ProblemSubmissionStatsPort {

    @DubboReference(group = "backend-submission", version = "1.1.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ProblemSubmissionStatsPort problemSubmissionStatsPort;

    @Override
    public long countCreatedSince(LocalDateTime from) {
        return problemSubmissionStatsPort.countCreatedSince(from);
    }

    @Override
    public long countAcceptedSince(LocalDateTime from) {
        return problemSubmissionStatsPort.countAcceptedSince(from);
    }

    @Override
    public long countByProblemId(Long problemId) {
        return problemSubmissionStatsPort.countByProblemId(problemId);
    }

    @Override
    public long countAcceptedByProblemId(Long problemId) {
        return problemSubmissionStatsPort.countAcceptedByProblemId(problemId);
    }

    @Override
    public Map<Long, Long> countByProblemIds(List<Long> problemIds) {
        return problemSubmissionStatsPort.countByProblemIds(problemIds);
    }

    @Override
    public Map<Long, Long> countAcceptedByProblemIds(List<Long> problemIds) {
        return problemSubmissionStatsPort.countAcceptedByProblemIds(problemIds);
    }

    @Override
    public List<ProblemDifficultyCompletion> countProblemCompletionByDifficulty(
            Map<Long, String> difficultyByProblemId) {
        return problemSubmissionStatsPort.countProblemCompletionByDifficulty(difficultyByProblemId);
    }

    @Override
    public List<ProblemTrend> findTrendingProblems(LocalDateTime from, int limit) {
        return problemSubmissionStatsPort.findTrendingProblems(from, limit);
    }
}
