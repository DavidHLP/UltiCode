package com.ulticode.judge.adapter;

import com.ulticode.app.api.dto.ProblemJudgingCaseDTO;
import com.ulticode.app.api.service.ProblemJudgingCaseReadPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/** Judge-side RPC adapter for App-owned canonical test cases. */
@Component
@Primary
public class RemoteProblemJudgingCaseReadAdapter implements ProblemJudgingCaseReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 5000, retries = 0, check = false)
    private ProblemJudgingCaseReadPort problemJudgingCaseReadPort;

    @Override
    public List<ProblemJudgingCaseDTO> loadCases(long problemId) {
        return problemJudgingCaseReadPort.loadCases(problemId);
    }
}
