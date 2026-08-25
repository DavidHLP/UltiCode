package com.ulticode.judge.adapter;

import com.ulticode.app.api.dto.ProblemJudgingCaseDTO;
import com.ulticode.app.api.service.ProblemJudgingCaseReadPort;
import com.ulticode.common.rpc.RpcPolicy;
import java.util.List;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Judge-side RPC adapter for App-owned canonical test cases. */
@Component
@Primary
public class RemoteProblemJudgingCaseReadAdapter implements ProblemJudgingCaseReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ProblemJudgingCaseReadPort problemJudgingCaseReadPort;

    @Override
    public List<ProblemJudgingCaseDTO> loadCases(long problemId) {
        return problemJudgingCaseReadPort.loadCases(problemId);
    }
}
