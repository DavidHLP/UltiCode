package com.ulticode.modules.forum.port;

import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Default {@link ForumUserReadPort} adapter.
 *
 * <p>Resolves user identity via the user-profile mapper (local MyBatis read)
 * falling back to the {@code IdentityQueryService} Dubbo RPC when the local
 * row is absent (newly registered users, cross-service lookups).
 *
 * <p>P7-RELOCATE-FORUM-001: mirrors
 * {@code DefaultSolutionUserReadAdapter} for the forum family.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultForumUserReadAdapter implements ForumUserReadPort {

    @DubboReference(group = "backend-auth", check = false)
    private IdentityQueryService identityQueryService;

    private final UserProfileMapper userProfileMapper;

    @Override
    public UserSummary findById(String userId) {
        if (userId == null) {
            return null;
        }
        Map<String, UserSummary> map = findAllById(List.of(userId));
        return map.get(userId);
    }

    @Override
    public Map<String, UserSummary> findAllById(Iterable<String> userIds) {
        if (userIds == null) {
            return Map.of();
        }
        Set<String> cleanIds = StreamSupport.stream(userIds.spliterator(), false)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (cleanIds.isEmpty()) {
            return Map.of();
        }

        // Local profile read: UserProfile.accountId = forum user ID (UUID),
        // UserProfile.name = username, UserProfile.avatar = avatar URL
        List<UserProfile> profiles = userProfileMapper.selectBatchIds(cleanIds);
        Map<String, UserProfile> profileMap = profiles.stream()
                .collect(Collectors.toMap(UserProfile::getAccountId, p -> p, (a, b) -> a));

        // Batch identity fallback for usernames not in local profile
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

        Map<String, UserSummary> result = new HashMap<>();
        for (String id : cleanIds) {
            UserProfile profile = profileMap.get(id);
            if (profile != null || usernameMap.containsKey(id)) {
                // profile.getName() is the username field (display name)
                String username = profile != null && profile.getName() != null
                        ? profile.getName()
                        : usernameMap.getOrDefault(id, id);
                String avatar = profile != null ? profile.getAvatar() : null;
                result.put(id, new UserSummary(id, username, avatar));
            }
        }
        return result;
    }
}
