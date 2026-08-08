package com.ulticode.modules.backup.adapter;

import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.modules.backup.port.UserLookupPort;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Production {@link UserLookupPort} adapter backed by
 * {@link IdentityQueryService#batchGetIdentity(Set)}.
 *
 * <p>{@code null} or empty input returns an empty map and unknown IDs are
 * omitted. Transport, result, payload, and malformed-row failures fail loud
 * rather than masquerading as an empty identity set.
 *
 * <p>Normalises the input to a {@link LinkedHashSet} before issuing a single
 * RPC call so duplicate IDs do not cause redundant provider lookups.
 *
 * @see IdentityQueryService#batchGetIdentity(Set)
 */
@Component
public class IdentityQueryServiceAdapter implements UserLookupPort {

    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private IdentityQueryService identityQueryService;

    public IdentityQueryServiceAdapter() {
    }

    IdentityQueryServiceAdapter(IdentityQueryService identityQueryService) {
        this.identityQueryService = identityQueryService;
    }

    @Override
    public Map<String, String> findUsernamesByIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<String> deduped = new LinkedHashSet<>(userIds);
        if (deduped.contains(null)) {
            throw identityQueryFailure("Identity query contains a null accountId");
        }

        RpcResult<List<UserIdentityDTO>> rpcResult;
        try {
            rpcResult = identityQueryService.batchGetIdentity(deduped);
        } catch (RuntimeException exception) {
            throw new BusinessException(AdminErrorCode.IDENTITY_QUERY_FAILED,
                    "Identity query invocation failed", exception);
        }
        if (rpcResult == null) {
            throw identityQueryFailure("batchGetIdentity returned null RpcResult");
        }
        if (!rpcResult.success()) {
            throw identityQueryFailure("Identity query failed: " + rpcResult.error());
        }

        List<UserIdentityDTO> data = rpcResult.data();
        if (data == null) {
            throw identityQueryFailure("batchGetIdentity returned null payload");
        }

        Map<String, String> result = new LinkedHashMap<>(data.size());
        for (UserIdentityDTO row : data) {
            if (row == null || row.accountId() == null || row.username() == null) {
                throw identityQueryFailure("batchGetIdentity returned a malformed identity row");
            }
            result.put(row.accountId(), row.username());
        }
        return result;
    }

    private static BusinessException identityQueryFailure(String message) {
        return new BusinessException(AdminErrorCode.IDENTITY_QUERY_FAILED, message);
    }

}
