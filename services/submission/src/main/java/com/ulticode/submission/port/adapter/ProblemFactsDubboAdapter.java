package com.ulticode.submission.port.adapter;

import com.ulticode.app.api.service.ProblemFactsPort;
import org.apache.dubbo.config.annotation.DubboReference;

import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Dubbo adapter for {@link ProblemFactsPort} — problem display facts are
 * owned by {@code backend-app} and read through its existing provider.
 *
 * <p>SPLIT-003 slice-2: the submission owner keeps the problem-facts
 * dependency on the App owner via a narrow port reference; it does not read
 * problem tables.
 */
@Component
public class ProblemFactsDubboAdapter implements ProblemFactsPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 5000, retries = 0, check = false)
    private ProblemFactsPort appProblemFacts;

    @Override
    public ProblemDisplayFacts findDisplayFacts(Long problemId) {
        return appProblemFacts.findDisplayFacts(problemId);
    }

    @Override
    public String findStarterCode(Long problemId, String language) {
        return appProblemFacts.findStarterCode(problemId, language);
    }

    @Override
    public Map<Long, ProblemDisplayFacts> findDisplayFactsBatch(Collection<Long> problemIds) {
        return appProblemFacts.findDisplayFactsBatch(problemIds);
    }

    @Override
    public ProblemLimits findLimits(Long problemId) {
        return appProblemFacts.findLimits(problemId);
    }

    @Override
    public ContestProblemFacts findContestProblemFacts(Long contestProblemId) {
        return appProblemFacts.findContestProblemFacts(contestProblemId);
    }
}
