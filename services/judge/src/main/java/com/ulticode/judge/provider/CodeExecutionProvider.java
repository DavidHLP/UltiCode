package com.ulticode.judge.provider;

import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.app.api.dto.RunSubmissionDTO;
import com.ulticode.app.api.service.CodeExecutionPort;
import com.ulticode.modules.submission.service.CodeExecutionService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/** Judge provider for synchronous user-supplied code preview execution. */
@DubboService(group = "backend-judge", version = "1.0.0")
@RequiredArgsConstructor
public class CodeExecutionProvider implements CodeExecutionPort {

    private final CodeExecutionService delegate;

    @Override
    public RunResultDTO execute(RunSubmissionDTO runDto, Long problemId, String userId) {
        return delegate.execute(runDto, problemId, userId);
    }
}
