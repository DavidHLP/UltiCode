package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.ProblemCompletionReportDTO;
import com.ulticode.app.api.service.ProblemAnalyticsReadPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Admin consumer adapter for App-owned problem completion analytics.
 */
@Primary
@Component
public class DubboProblemAnalyticsReadAdapter implements ProblemAnalyticsReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ProblemAnalyticsReadPort problemAnalyticsReadPort;

    @Override
    public ProblemCompletionReportDTO loadProblemCompletionReport(Integer days) {
        return problemAnalyticsReadPort.loadProblemCompletionReport(days);
    }
}
