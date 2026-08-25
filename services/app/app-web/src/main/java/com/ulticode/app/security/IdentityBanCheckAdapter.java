package com.ulticode.app.security;

import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * App-side {@link BanCheckPort} adapter backed by
 * {@link IdentityQueryService} via Dubbo RPC.
 *
 * <p>Replaces the legacy {@code UserBanCheckAdapter} which read
 * {@code UserMapper} directly. This adapter queries the auth service's
 * identity projection, which carries the {@code banned} flag.
 *
 * <p><b>Fail-safe:</b> if the RPC fails or returns null, the adapter
 * returns {@code false} (not banned). This matches the legacy contract:
 * ban check is non-throwing, and a transient RPC failure should not
 * block legitimate users. The aspect itself is the last line of
 * defense — if the aspect throws, the request is blocked.
 *
 * <p>P7-RELOCATE-SOLUTION-001: required when backend-app stopped depending
 * on backend-legacy.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityBanCheckAdapter implements BanCheckPort {

    @DubboReference(group = "backend-auth", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private IdentityQueryService identityQueryService;

    @Override
    public boolean isBanned(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (identityQueryService == null) {
            log.debug("IdentityQueryService not available; ban check skipped for user {}", userId);
            return false;
        }
        try {
            RpcResult<UserIdentityDTO> result = identityQueryService.getIdentity(userId);
            if (result == null || !result.success() || result.data() == null) {
                log.debug("Ban check RPC returned no data for user {}", userId);
                return false;
            }
            return result.data().banned();
        } catch (Exception e) {
            log.warn("Ban check RPC failed for user {}: {}", userId, e.getMessage());
            return false;
        }
    }
}
