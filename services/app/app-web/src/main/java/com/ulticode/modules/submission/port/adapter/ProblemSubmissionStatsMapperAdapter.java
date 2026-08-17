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
import java.util.List;

/**
 * Production adapter for {@link ProblemSubmissionStatsPort}, backed by
 * {@code SubmissionMapper}. Confines the problem-analytics submission reads to
 * the submission module; the problem projections depend on the port.
 */
@Component
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
    public List<ProblemDifficultyCompletion> countProblemCompletionByDifficulty() {
        return submissionMapper.countProblemCompletionByDifficulty();
    }

    @Override
    public List<ProblemTrend> findTrendingProblems(LocalDateTime from, int limit) {
        return submissionMapper.findTrendingProblems(from, limit);
    }
}
