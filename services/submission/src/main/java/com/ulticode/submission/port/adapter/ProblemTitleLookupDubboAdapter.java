package com.ulticode.submission.port.adapter;

import com.ulticode.app.api.service.ProblemTitleLookupPort;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

/** Submission adapter for the one App Problem lookup it consumes. */
@Component
@Primary
public class ProblemTitleLookupDubboAdapter implements ProblemTitleLookupPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ProblemTitleLookupPort appProblemTitles;

    @Override
    public List<Long> searchProblemIdsByTitle(String title) {
        return appProblemTitles.searchProblemIdsByTitle(title);
    }
}
