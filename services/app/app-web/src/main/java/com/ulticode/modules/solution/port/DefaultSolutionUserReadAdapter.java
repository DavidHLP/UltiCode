package com.ulticode.modules.solution.port;

import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter implementing {@link SolutionUserReadPort} backed by
 * {@link UserProfileMapper} and {@link IdentityQueryService}.
 *
 * <p>App-side adapter following the follow module's
 * {@code DefaultUserReadAdapter} precedent. Replaces the transitional
 * adapter that used {@code UserReadProjection} from backend-legacy.
 *
 * <p>P7-RELOCATE-SOLUTION-001: fully decouples the solution projection
 * from backend-legacy's user module.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSolutionUserReadAdapter implements SolutionUserReadPort {

    private final UserProfileMapper userProfileMapper;

    @DubboReference(group = "backend-auth", check = false)
    private IdentityQueryService identityQueryService;

    @Override
    public UserSummary findById(String userId) {
        if (userId == null) {
            return null;
        }
        Map<String, UserSummary> map = findAllById(List.of(userId));
        return map.get(userId);
    }

    @Override
    public Map<String, UserSummary> findAllById(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Set<String> cleanIds = userIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());
        if (cleanIds.isEmpty()) {
            return Map.of();
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

        Map<String, UserSummary> result = new HashMap<>();
        for (String id : cleanIds) {
            UserProfile profile = profileMap.get(id);
            if (profile != null || usernameMap.containsKey(id)) {
                String displayName = profile != null && profile.getName() != null
                        ? profile.getName()
                        : usernameMap.getOrDefault(id, id);
                String avatar = profile != null ? profile.getAvatar() : null;
                result.put(id, new UserSummary(id, displayName, avatar));
            }
        }
        return result;
    }
}
