package com.ulticode.app.userprofile.provider;

import com.ulticode.app.api.dto.NotificationUserInfo;
import com.ulticode.app.api.service.UserReadPort;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcPolicy;
import com.ulticode.common.rpc.RpcResult;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

/**
 * App-side adapter for {@link UserReadPort} — backed by
 * {@link UserProfileMapper} (App-owned name/avatar) and
 * {@link IdentityQueryService} (Auth-owned username and active-recipient
 * enumeration via Dubbo).
 *
 * <p>P7-RELOCATE-PROBLEMLIST-001: the problem-list projection needs
 * author display name + username. {@code name} comes from the App-owned
 * {@code user_profiles} table; {@code username} from the Auth-owned
 * {@code users} table via the identity RPC — preserving the owner boundary
 * without a cross-owner SQL join.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultUserReadPort implements UserReadPort {

    private final UserProfileMapper userProfileMapper;

    @DubboReference(group = "backend-auth", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private IdentityQueryService identityQueryService;

    @Override
    public NotificationUserInfo findById(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        List<NotificationUserInfo> results = findByIds(List.of(userId));
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public List<NotificationUserInfo> findByIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> cleanIds = userIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (cleanIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<UserProfile> profiles = userProfileMapper.selectBatchIds(cleanIds);
        Map<String, UserProfile> profileMap = profiles.stream()
                .collect(Collectors.toMap(UserProfile::getAccountId, p -> p, (a, b) -> a));

        Map<String, String> usernameMap = new HashMap<>();
        if (identityQueryService != null) {
            try {
                RpcResult<List<UserIdentityDTO>> res = identityQueryService.batchGetIdentity(cleanIds);
                if (res != null && res.success() && res.data() != null) {
                    res.data().forEach(dto -> usernameMap.put(dto.accountId(), dto.username()));
                }
            } catch (Exception e) {
                log.warn("Batch identity lookup failed for userIds: {}", e.getMessage());
            }
        }

        List<NotificationUserInfo> result = new ArrayList<>();
        for (String id : cleanIds) {
            UserProfile profile = profileMap.get(id);
            if (profile != null || usernameMap.containsKey(id)) {
                String username = usernameMap.getOrDefault(id, profile != null ? profile.getName() : id);
                String name = profile != null ? profile.getName() : null;
                String email = null;
                result.add(new NotificationUserInfo(id, username, email, name));
            }
        }
        return result;
    }

    @Override
    public List<String> findAllActiveIds() {
        if (identityQueryService == null) {
            return Collections.emptyList();
        }
        try {
            RpcResult<List<String>> response = identityQueryService.findActiveAccountIds();
            if (response == null || !response.success() || response.data() == null) {
                throw new IllegalStateException("Auth active-account lookup failed");
            }
            return response.data().stream()
                    .filter(Objects::nonNull)
                    .filter(id -> !id.isBlank())
                    .distinct()
                    .toList();
        } catch (Exception e) {
            throw new IllegalStateException("Auth active-account lookup failed", e);
        }
    }
}
