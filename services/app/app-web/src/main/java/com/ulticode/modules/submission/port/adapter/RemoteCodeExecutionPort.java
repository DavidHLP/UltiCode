package com.ulticode.modules.submission.port.adapter;

import com.ulticode.app.api.dto.RunResultDTO;
import com.ulticode.app.api.dto.RunSubmissionDTO;
import com.ulticode.app.api.service.CodeExecutionPort;
import com.ulticode.app.error.ProblemErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcPolicy;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.RpcException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** App adapter for the Judge-owned synchronous code execution seam. */
@Component
@Primary
@ConditionalOnExpression("'${app.runtime.mode:dev-lite}' != 'legacy-rollback'")
@Slf4j
public class RemoteCodeExecutionPort implements CodeExecutionPort {

    @DubboReference(group = "backend-judge", version = "1.0.0",
            timeout = RpcPolicy.EXECUTION_TIMEOUT_MS, retries = RpcPolicy.EXECUTION_RETRIES,
            check = false)
    private CodeExecutionPort judgeExecution;

    @Override
    public RunResultDTO execute(RunSubmissionDTO runDto, Long problemId, String userId) {
        try {
            return judgeExecution.execute(runDto, problemId, userId);
        } catch (RpcException e) {
            log.warn("backend-judge code execution RPC unavailable: {}", e.getMessage());
            throw new BusinessException(ProblemErrorCode.CODE_EXECUTION_UNAVAILABLE, e);
        }
    }
}
