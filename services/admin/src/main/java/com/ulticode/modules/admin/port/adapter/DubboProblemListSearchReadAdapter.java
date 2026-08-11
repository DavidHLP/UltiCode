package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.dto.ProblemListSummaryDTO;
import com.ulticode.app.api.service.ProblemListSearchReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.rpc.RpcPolicy;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Dubbo consumer adapter registering {@link ProblemListSearchReadPort} as
 * a local admin bean, backed by the {@code backend-app} provider
 * ({@code com.ulticode.app.dubbo.provider.ProblemListSearchReadProvider}).
 *
 * <p>Admin services keep depending on the entity-free port contract; this
 * adapter is the only local bean of that type. Read references use the
 * query RPC policy (800 ms, one retry) per {@link RpcPolicy}.
 */
@Primary
@Component
public class DubboProblemListSearchReadAdapter implements ProblemListSearchReadPort {

    @DubboReference(group = "backend-app", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private ProblemListSearchReadPort problemListSearchReadPort;

    @Override
    public PageResult<ProblemListSummaryDTO> searchAdminLists(
            String search, Boolean isFeatured, Boolean isPublic,
            String sortBy, String sortOrder, int page, int limit) {
        return problemListSearchReadPort.searchAdminLists(
                search, isFeatured, isPublic, sortBy, sortOrder, page, limit);
    }
}
