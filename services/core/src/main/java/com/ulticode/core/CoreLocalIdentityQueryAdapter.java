package com.ulticode.core;

import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Core local Adapter for the Auth read-only identity Seam.
 *
 * <p>Because child contexts run with {@code dubbo.enabled=false}, the
 * {@code @DubboReference} in {@code AccountReadAdapter} cannot fire.
 * This adapter provides the same {@link IdentityQueryService} contract
 * by delegating in-process to the Auth child context's
 * {@code UserIdentityQueryProvider} bean.
 *
 * <p>Mirrors the established pattern from
 * {@link CoreLocalAuthorizationMutationAdapter}.
 */
@Component
public final class CoreLocalIdentityQueryAdapter implements IdentityQueryService {

    private final CoreOwnerContextManager ownerContexts;

    public CoreLocalIdentityQueryAdapter(CoreOwnerContextManager ownerContexts) {
        this.ownerContexts = ownerContexts;
    }

    @Override
    public RpcResult<UserIdentityDTO> getIdentity(String accountId) {
        return ownerContexts.bean("auth", IdentityQueryService.class).getIdentity(accountId);
    }

    @Override
    public RpcResult<List<UserIdentityDTO>> batchGetIdentity(Set<String> accountIds) {
        return ownerContexts.bean("auth", IdentityQueryService.class).batchGetIdentity(accountIds);
    }

    @Override
    public RpcResult<List<String>> findActiveAccountIds() {
        return ownerContexts.bean("auth", IdentityQueryService.class).findActiveAccountIds();
    }
}
