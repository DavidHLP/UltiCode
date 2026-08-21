package com.ulticode.admin.security.jwt;

import com.ulticode.common.auth.AccountInfo;
import com.ulticode.common.security.AccountReadPort;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Admin-local adapter implementing {@link AccountReadPort} for WebSocket
 * authentication (P7-RELOCATE). Reads account state (active/banned) via the
 * Auth-owned public {@link IdentityQueryService} Dubbo contract instead of
 * the App-private {@code UserFactsProjection} Q-read.
 *
 * <p>The Dubbo reference is {@code check=false}/{@code required=false} so the
 * admin context loads even when the Auth provider is down; an unavailable or
 * failing lookup yields {@link Optional#empty()} (WebSocket auth denied).
 */
@Slf4j
@Component
public class AccountReadAdapter implements AccountReadPort {

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private IdentityQueryService identityQueryService;

    @Override
    public Optional<AccountInfo> findById(String userId) {
        if (identityQueryService == null) {
            log.warn("IdentityQueryService unavailable; account lookup for {} skipped", userId);
            return Optional.empty();
        }
        RpcResult<UserIdentityDTO> result;
        try {
            result = identityQueryService.getIdentity(userId);
        } catch (RuntimeException e) {
            log.warn("Identity lookup failed for account {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
        if (result == null || !result.success() || result.data() == null) {
            log.warn("Identity lookup failed for account {}: {}", userId,
                    result != null ? result.error() : "null rpc result");
            return Optional.empty();
        }
        UserIdentityDTO user = result.data();
        return Optional.of(new AccountInfo(
                user.accountId(),
                user.username(),
                user.role(),
                user.active(),
                user.banned()));
    }
}
