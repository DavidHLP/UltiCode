package com.ulticode.modules.submission.port.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.submission.api.dto.ProblemDifficultyCompletion;
import com.ulticode.submission.api.dto.ProblemTrend;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.submission.api.service.ProblemSubmissionStatsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Production adapter for {@link ProblemSubmissionStatsPort}, backed by
 * {@code SubmissionMapper}. Confines the problem-analytics submission reads to
 * the submission module; the problem projections depend on the port.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression(
        "'${app.runtime.mode:dev-lite}' == 'legacy-rollback'")
@RequiredArgsConstructor
public class ProblemSubmissionStatsMapperAdapter implements ProblemSubmissionStatsPort {

    private final SubmissionMapper submissionMapper;

    @Override
    public long countCreatedSince(LocalDateTime from) {
        Long n = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>().ge(Submission::getCreatedAt, from));
        return n == null ? 0L : n;
    }

    @Override
    public long countAcceptedSince(LocalDateTime from) {
        Long n = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>()
                        .ge(Submission::getCreatedAt, from)
                        .eq(Submission::getStatus, "Accepted"));
        return n == null ? 0L : n;
    }

    @Override
    public long countByProblemId(Long problemId) {
        Long n = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>().eq(Submission::getProblemId, problemId));
        return n == null ? 0L : n;
    }

    @Override
    public long countAcceptedByProblemId(Long problemId) {
        Long n = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>()
                        .eq(Submission::getProblemId, problemId)
                        .eq(Submission::getStatus, "Accepted"));
        return n == null ? 0L : n;
    }

    @Override
    public Map<Long, Long> countByProblemIds(List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Long problemId : problemIds) {
            Long count = submissionMapper.selectCount(
                    new LambdaQueryWrapper<Submission>().eq(Submission::getProblemId, problemId));
            result.put(problemId, count == null ? 0L : count);
        }
        return result;
    }

    @Override
    public Map<Long, Long> countAcceptedByProblemIds(List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new LinkedHashMap<>();
        for (Long problemId : problemIds) {
            Long count = submissionMapper.selectCount(
                    new LambdaQueryWrapper<Submission>()
                            .eq(Submission::getProblemId, problemId)
                            .eq(Submission::getStatus, "Accepted"));
            result.put(problemId, count == null ? 0L : count);
        }
        return result;
    }

    @Override
    public List<ProblemDifficultyCompletion> countProblemCompletionByDifficulty(
            Map<Long, String> difficultyByProblemId) {
        return submissionMapper.countProblemCompletionByDifficulty();
    }

    @Override
    public List<ProblemTrend> findTrendingProblems(LocalDateTime from, int limit) {
        return submissionMapper.findTrendingProblems(from, limit);
    }
}
