package com.ulticode.submission.dubbo.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.submission.api.dto.ProblemDifficultyCompletion;
import com.ulticode.submission.api.dto.ProblemTrend;
import com.ulticode.submission.api.service.ProblemSubmissionStatsPort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Exposes pure Submission-owner statistics used by App Problem projections. */
@DubboService(group = "backend-submission", version = "1.1.0")
@RequiredArgsConstructor
public class ProblemSubmissionStatsProvider implements ProblemSubmissionStatsPort {

    private final SubmissionMapper submissionMapper;

    @Override
    public long countCreatedSince(LocalDateTime from) {
        Long count = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>().ge(Submission::getCreatedAt, from));
        return count == null ? 0L : count;
    }

    @Override
    public long countAcceptedSince(LocalDateTime from) {
        Long count = submissionMapper.selectCount(new LambdaQueryWrapper<Submission>()
                .ge(Submission::getCreatedAt, from)
                .eq(Submission::getStatus, "Accepted"));
        return count == null ? 0L : count;
    }

    @Override
    public long countByProblemId(Long problemId) {
        Long count = submissionMapper.selectCount(
                new LambdaQueryWrapper<Submission>().eq(Submission::getProblemId, problemId));
        return count == null ? 0L : count;
    }

    @Override
    public long countAcceptedByProblemId(Long problemId) {
        Long count = submissionMapper.selectCount(new LambdaQueryWrapper<Submission>()
                .eq(Submission::getProblemId, problemId)
                .eq(Submission::getStatus, "Accepted"));
        return count == null ? 0L : count;
    }

    @Override
    public Map<Long, Long> countByProblemIds(List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new LinkedHashMap<>();
        for (SubmissionMapper.ProblemSubmissionCount row : safe(
                submissionMapper.countByProblemIds(problemIds))) {
            if (row != null && row.problemId() != null) {
                result.put(row.problemId(), row.count() == null ? 0L : row.count());
            }
        }
        return result;
    }

    @Override
    public Map<Long, Long> countAcceptedByProblemIds(List<Long> problemIds) {
        if (problemIds == null || problemIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new LinkedHashMap<>();
        for (SubmissionMapper.ProblemSubmissionCount row : safe(
                submissionMapper.countAcceptedByProblemIds(problemIds))) {
            if (row != null && row.problemId() != null) {
                result.put(row.problemId(), row.count() == null ? 0L : row.count());
            }
        }
        return result;
    }

    @Override
    public List<ProblemDifficultyCompletion> countProblemCompletionByDifficulty(
            Map<Long, String> difficultyByProblemId) {
        Map<String, Long> totalByDifficulty = new LinkedHashMap<>();
        if (difficultyByProblemId != null) {
            for (String difficulty : difficultyByProblemId.values()) {
                String normalized = normalizeDifficulty(difficulty);
                if (normalized != null) {
                    totalByDifficulty.merge(normalized, 1L, Long::sum);
                }
            }
        }
        Map<String, Long> solvedByDifficulty = new LinkedHashMap<>();
        for (SubmissionMapper.ProblemAcceptanceCount row : safe(
                submissionMapper.countAcceptedByProblem())) {
            if (row == null || row.problemId() == null) {
                continue;
            }
            String difficulty = difficultyByProblemId == null ? null
                    : normalizeDifficulty(difficultyByProblemId.get(row.problemId()));
            if (difficulty != null) {
                if (row.acceptedCount() != null && row.acceptedCount() > 0) {
                    solvedByDifficulty.merge(difficulty, 1L, Long::sum);
                }
            }
        }

        return totalByDifficulty.entrySet().stream()
                .map(entry -> new ProblemDifficultyCompletion(
                        entry.getKey(), entry.getValue(),
                        solvedByDifficulty.getOrDefault(entry.getKey(), 0L)))
                .toList();
    }

    @Override
    public List<ProblemTrend> findTrendingProblems(LocalDateTime from, int limit) {
        return safe(submissionMapper.findTrendingProblems(from, limit));
    }

    private static String normalizeDifficulty(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
