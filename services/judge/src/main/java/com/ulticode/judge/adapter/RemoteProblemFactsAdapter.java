package com.ulticode.judge.adapter;

import com.ulticode.app.api.service.ProblemFactsPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/** Judge-side RPC adapter for App-owned problem facts. */
@Component
public class RemoteProblemFactsAdapter implements ProblemFactsPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 5000, retries = 0, check = false)
    private ProblemFactsPort delegate;

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
