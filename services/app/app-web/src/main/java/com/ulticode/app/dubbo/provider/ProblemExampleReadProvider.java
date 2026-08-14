package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.ProblemExampleDTO;
import com.ulticode.app.api.service.ProblemExampleReadPort;
import com.ulticode.modules.problem.port.DefaultProblemExampleReadPort;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.context.annotation.Profile;

import java.util.List;

/** Exposes legacy sample cases while the canonical test-case migration is staged. */
@DubboService(group = "backend-app", version = "1.0.0")
@Profile("!test")
@RequiredArgsConstructor
public class ProblemExampleReadProvider implements ProblemExampleReadPort {

    private final DefaultProblemExampleReadPort delegate;

    @Override
    public List<ProblemExampleDTO> findByProblemId(Long problemId) {
        return delegate.findByProblemId(problemId);
    }
}
