package com.ulticode.app.dubbo.provider;

import com.ulticode.app.api.service.ProblemTitleLookupPort;
import com.ulticode.modules.problem.adapter.DefaultProblemAdminReadAdapter;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/** App-owner provider for Submission's narrow problem-title lookup. */
@DubboService(group = "backend-app", version = "1.0.0")
@RequiredArgsConstructor
public class ProblemTitleLookupProvider implements ProblemTitleLookupPort {

    private final DefaultProblemAdminReadAdapter delegate;

    @Override
    public List<Long> searchProblemIdsByTitle(String title) {
        return delegate.searchProblemIdsByTitle(title);
    }
}
