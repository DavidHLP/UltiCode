package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.modules.problem.port.ProblemFactsAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.context.annotation.Profile;

/** Exposes App-owned problem facts to the external judge runtime. */
@DubboService(group = "backend-app", version = "1.0.0")
@Profile("!test")
@RequiredArgsConstructor
public class ProblemFactsProvider implements ProblemFactsPort {

    private final ProblemFactsAdapter delegate;

    @Override
    public ProblemDisplayFacts findDisplayFacts(Long problemId) {
        return delegate.findDisplayFacts(problemId);
    }

    @Override
    public ProblemLimits findLimits(Long problemId) {
        return delegate.findLimits(problemId);
    }

    @Override
    public String findStarterCode(Long problemId, String language) {
        return delegate.findStarterCode(problemId, language);
    }

    @Override
    public ContestProblemFacts findContestProblemFacts(Long problemId) {
        return delegate.findContestProblemFacts(problemId);
    }
}
