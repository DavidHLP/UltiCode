package com.ulticode.app.dubbo.provider;

import com.ulticode.common.dto.DifficultyCountDTO;
import com.ulticode.submission.api.dto.SubmissionDateCountDTO;
import com.ulticode.submission.api.service.SubmissionUserStatsPort;
import com.ulticode.modules.submission.port.adapter.SubmissionUserStatsMapperAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * Dubbo provider for per-user submission statistics owned by App.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class SubmissionUserStatsProvider implements SubmissionUserStatsPort {

    private final SubmissionUserStatsMapperAdapter delegate;

    @Override
    public Long countAcceptedProblemsByUserId(String userId) {
        return delegate.countAcceptedProblemsByUserId(userId);
    }

    @Override
    public Long countByUserId(String userId) {
        return delegate.countByUserId(userId);
    }

    @Override
    public Long countTotalSubmissionsByUserId(String userId) {
        return delegate.countTotalSubmissionsByUserId(userId);
    }

    @Override
    public Double calculateAcceptanceRateByUserId(String userId) {
        return delegate.calculateAcceptanceRateByUserId(userId);
    }

    @Override
    public Integer findGlobalRankByUserId(String userId) {
        return delegate.findGlobalRankByUserId(userId);
    }

    @Override
    public List<DifficultyCountDTO> countAcceptedProblemsByDifficulty(String userId) {
        return delegate.countAcceptedProblemsByDifficulty(userId);
    }

    @Override
    public List<SubmissionDateCountDTO> findSubmissionCountsByDate(String userId, Integer year) {
        return delegate.findSubmissionCountsByDate(userId, year);
    }
}
