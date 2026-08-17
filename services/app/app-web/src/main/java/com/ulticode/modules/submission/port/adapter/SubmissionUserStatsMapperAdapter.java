package com.ulticode.modules.submission.port.adapter;

import com.ulticode.submission.api.dto.SubmissionDateCountDTO;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.submission.api.service.SubmissionUserStatsPort;
import com.ulticode.common.dto.DifficultyCountDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Production adapter for {@link SubmissionUserStatsPort}, backed by
 * {@code SubmissionMapper}. The only place outside the submission module's
 * write/fence paths that touches the mapper for per-user stat reads;
 * cross-module callers depend on the port.
 */
@Component
@Primary
@RequiredArgsConstructor
public class SubmissionUserStatsMapperAdapter implements SubmissionUserStatsPort {

    private final SubmissionMapper submissionMapper;

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
    public List<DifficultyCountDTO> countAcceptedProblemsByDifficulty(String userId) {
        return submissionMapper.countAcceptedProblemsByDifficulty(userId);
    }

    @Override
    public List<SubmissionDateCountDTO> findSubmissionCountsByDate(String userId, Integer year) {
        return submissionMapper.findSubmissionCountsByDate(userId, year);
    }
}
