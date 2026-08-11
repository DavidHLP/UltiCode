package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.dto.ProblemCompletionReportDTO;
import com.ulticode.app.api.service.ProblemAnalyticsReadPort;
import com.ulticode.modules.problem.projection.DefaultProblemAnalyticsProjection;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * Dubbo provider for App-owned problem completion analytics.
 */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ProblemAnalyticsReadProvider implements ProblemAnalyticsReadPort {

    private final DefaultProblemAnalyticsProjection delegate;

    @Override
    public ProblemCompletionReportDTO loadProblemCompletionReport(Integer days) {
        return delegate.loadProblemCompletionReport(days);
    }
}
