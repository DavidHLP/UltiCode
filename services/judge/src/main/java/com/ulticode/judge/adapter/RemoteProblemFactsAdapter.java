package com.ulticode.judge.adapter;

import com.ulticode.app.api.service.ProblemFactsPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/** Judge-side RPC adapter for App-owned problem facts. */
@Component
@Primary
public class RemoteProblemFactsAdapter implements ProblemFactsPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 5000, retries = 0, check = false)
    private ProblemFactsPort problemFactsPort;

    @Override
    public ProblemDisplayFacts findDisplayFacts(Long problemId) {
        return problemFactsPort.findDisplayFacts(problemId);
    }

    @Override
    public Map<Long, ProblemDisplayFacts> findDisplayFactsBatch(Collection<Long> problemIds) {
        return problemFactsPort.findDisplayFactsBatch(problemIds);
    }

    @Override
    public ProblemLimits findLimits(Long problemId) {
        return problemFactsPort.findLimits(problemId);
    }

    @Override
    public String findStarterCode(Long problemId, String language) {
        return problemFactsPort.findStarterCode(problemId, language);
    }

    @Override
    public ContestProblemFacts findContestProblemFacts(Long problemId) {
        return problemFactsPort.findContestProblemFacts(problemId);
    }
}
