package com.ulticode.judge.adapter;

import com.ulticode.app.api.dto.ProblemExampleDTO;
import com.ulticode.app.api.service.ProblemExampleReadPort;
import com.ulticode.common.rpc.RpcPolicy;
import java.util.List;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** Judge-side RPC adapter for legacy App-owned sample cases. */
@Component
@Primary
public class RemoteProblemExampleReadAdapter implements ProblemExampleReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ProblemExampleReadPort problemExampleReadPort;

    @Override
    public List<ProblemExampleDTO> findByProblemId(Long problemId) {
        return problemExampleReadPort.findByProblemId(problemId);
    }
}
