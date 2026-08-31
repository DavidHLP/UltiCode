package com.ulticode.app.security;

import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
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
 * <p><b>Fail-closed:</b> only an explicit Auth response may return
 * {@code false}. Unavailable, null or failed responses throw so a circuit-open
 * dependency can never be interpreted as "not banned".
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
            throw unavailable(null);
        }
        try {
            RpcResult<UserIdentityDTO> result = identityQueryService.getIdentity(userId);
            if (result == null || !result.success() || result.data() == null) {
                throw unavailable(null);
            }
            return result.data().banned();
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception e) {
            log.warn("Ban check RPC failed for user {}: {}", userId, e.getMessage());
            throw unavailable(e);
        }
    }

    private static BusinessException unavailable(Throwable cause) {
        return new BusinessException(
                BaseErrorCode.UNKNOWN_ERROR, "Unable to verify user ban status", cause);
    }
}
