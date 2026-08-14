package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.ProblemJudgingCaseDTO;
import com.ulticode.app.api.service.ProblemJudgingCaseReadPort;
import com.ulticode.modules.problem.port.DefaultProblemJudgingCaseReadPort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.context.annotation.Profile;

import java.util.List;

/** Exposes App-owned canonical judging cases to the external judge runtime. */
@DubboService(group = "backend-app", version = "1.0.0")
@Profile("!test")
@RequiredArgsConstructor
public class ProblemJudgingCaseReadProvider implements ProblemJudgingCaseReadPort {

    private final DefaultProblemJudgingCaseReadPort delegate;

    @Override
    public List<ProblemJudgingCaseDTO> loadCases(long problemId) {
        return delegate.loadCases(problemId);
    }
}
