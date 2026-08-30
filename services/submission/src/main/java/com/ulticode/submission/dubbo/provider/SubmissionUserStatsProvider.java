package com.ulticode.submission.dubbo.provider;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.common.dto.DifficultyCountDTO;
import com.ulticode.submission.api.dto.SubmissionDateCountDTO;
import com.ulticode.submission.api.service.SubmissionUserStatsPort;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Exposes Submission-owner per-user statistics without App table access. */
@DubboService(group = "backend-submission", version = "1.1.0")
@RequiredArgsConstructor
public class SubmissionUserStatsProvider implements SubmissionUserStatsPort {

    private final SubmissionMapper submissionMapper;
    private final ProblemFactsPort problemFacts;

    @Override
    public Long countAcceptedProblemsByUserId(String userId) {
        return submissionMapper.countAcceptedProblemsByUserId(userId);
    }

    @Override
    public Long countByUserId(String userId) {
        return submissionMapper.countByUserId(userId);
    }

    @Override
    public Long countTotalSubmissionsByUserId(String userId) {
        return submissionMapper.countTotalSubmissionsByUserId(userId);
    }

    @Override
    public Double calculateAcceptanceRateByUserId(String userId) {
        return submissionMapper.calculateAcceptanceRateByUserId(userId);
    }

    @Override
    public Integer findGlobalRankByUserId(String userId) {
        return submissionMapper.findGlobalRankByUserId(userId);
    }

    @Override
    public List<Long> findAcceptedProblemIdsByUserId(String userId) {
        return safe(submissionMapper.findAcceptedProblemIdsByUserId(userId));
    }

    @Override
    public List<DifficultyCountDTO> countAcceptedProblemsByDifficulty(String userId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Long problemId : safe(submissionMapper.findAcceptedProblemIdsByUserId(userId))) {
            ProblemFactsPort.ContestProblemFacts facts = problemFacts.findContestProblemFacts(problemId);
            String difficulty = facts == null ? null : normalizeDifficulty(facts.difficulty());
            if (difficulty != null) {
                counts.merge(difficulty, 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> new DifficultyCountDTO(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    public List<SubmissionDateCountDTO> findSubmissionCountsByDate(String userId, Integer year) {
        return safe(submissionMapper.findSubmissionCountsByDate(userId, year));
    }

    private static String normalizeDifficulty(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
