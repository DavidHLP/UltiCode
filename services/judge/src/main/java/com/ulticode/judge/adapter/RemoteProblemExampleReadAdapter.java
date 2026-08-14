package com.ulticode.judge.adapter;

import com.ulticode.app.api.dto.ProblemExampleDTO;
import com.ulticode.app.api.service.ProblemExampleReadPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;

/** Judge-side RPC adapter for legacy App-owned sample cases. */
@Component
public class RemoteProblemExampleReadAdapter implements ProblemExampleReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = 5000, retries = 0, check = false)
    private ProblemExampleReadPort delegate;

    @Override
    public List<ProblemExampleDTO> findByProblemId(Long problemId) {
        return delegate.findByProblemId(problemId);
    }
}
