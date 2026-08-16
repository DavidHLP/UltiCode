package com.ulticode.submission.port.adapter;

import com.ulticode.app.api.service.UserExistencePort;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * User-existence check for the submission owner.
 *
 * <p>The {@code users} identity is owned by {@code backend-auth}; this
 * adapter asks Auth's {@link IdentityQueryService} instead of reading any
 * user table (R1: no cross-service SQL). Mirrors the App-side
 * {@code DefaultSubmissionUserReadAdapter} auth fallback.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserExistenceDubboAdapter implements UserExistencePort {

    @DubboReference(group = "backend-auth", check = false)
    private IdentityQueryService identityQueryService;

    @Override
    public boolean existsById(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        if (identityQueryService == null) {
            log.warn("IdentityQueryService unavailable; user {} existence unknown", userId);
            return false;
        }
        try {
            RpcResult<List<UserIdentityDTO>> res = identityQueryService.batchGetIdentity(Set.of(userId));
            return res != null && res.success() && res.data() != null && !res.data().isEmpty();
        } catch (Exception e) {
            log.warn("Identity lookup failed for userId {}: {}", userId, e.getMessage());
            return false;
        }
    }
}
