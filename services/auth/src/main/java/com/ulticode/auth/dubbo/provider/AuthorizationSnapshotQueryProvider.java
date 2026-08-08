package com.ulticode.auth.dubbo.provider;

import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.error.AuthErrorCode;
import com.ulticode.auth.api.service.AuthorizationSnapshotService;
import com.ulticode.auth.service.AuthorizationSnapshotQuery;
import com.ulticode.common.rpc.RpcResult;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Dubbo transport adapter for the Auth-owned authorization snapshot query.
 */
@Component
@DubboService(group = "backend-auth", version = "1.0.0")
public class AuthorizationSnapshotQueryProvider implements AuthorizationSnapshotService {

    private static final String DEFAULT_TRACE_ID = "t-system";

    private final AuthorizationSnapshotQuery query;

    public AuthorizationSnapshotQueryProvider(AuthorizationSnapshotQuery query) {
        this.query = query;
    }

    @Override
    public RpcResult<AuthorizationSnapshotDTO> getSnapshot(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return RpcResult.failure(AuthErrorCode.ACCOUNT_NOT_FOUND, DEFAULT_TRACE_ID);
        }
        Optional<AuthorizationSnapshotDTO> snapshot = query.getSnapshot(accountId);
        return snapshot.map(value -> RpcResult.success(value, DEFAULT_TRACE_ID))
                .orElseGet(() -> RpcResult.failure(
                        AuthErrorCode.ACCOUNT_NOT_FOUND, DEFAULT_TRACE_ID));
    }

    @Override
    public RpcResult<List<AuthorizationSnapshotDTO>> batchGetSnapshot(
            Set<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return RpcResult.success(Collections.emptyList(), DEFAULT_TRACE_ID);
        }
        return RpcResult.success(
                query.batchGetSnapshot(accountIds), DEFAULT_TRACE_ID);
    }
}
