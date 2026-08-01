package com.ulticode.app.follow.provider;

import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.follow.port.UserReadPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Adapter backing UserReadPort via UserProfileMapper and IdentityQueryService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultUserReadAdapter implements UserReadPort {

    private final UserProfileMapper userProfileMapper;

    @DubboReference(check = false)
    private IdentityQueryService identityQueryService;

    @Override
    public boolean exists(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile != null) {
            return true;
        }
        if (identityQueryService != null) {
            try {
                RpcResult<UserIdentityDTO> res = identityQueryService.getIdentity(userId);
                return res != null && res.success() && res.data() != null;
            } catch (Exception e) {
                log.warn("Identity service check failed for user {}: {}", userId, e.getMessage());
            }
        }
        return false;
    }

    @Override
    public UserSummaryData findById(String userId) {
        if (userId == null) {
            return null;
        }
        Map<String, UserSummaryData> map = findByIds(List.of(userId));
        return map.get(userId);
    }

    @Override
    public Map<String, UserSummaryData> findByIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Set<String> cleanIds = userIds.stream()
                .filter(Objects::nonNull)
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

        Map<String, UserSummaryData> result = new HashMap<>();
        for (String id : cleanIds) {
            UserProfile profile = profileMap.get(id);
            if (profile != null || usernameMap.containsKey(id)) {
                String username = usernameMap.getOrDefault(id, profile != null ? profile.getName() : id);
                String avatar = profile != null ? profile.getAvatar() : null;
                String bio = profile != null ? profile.getBio() : null;
                result.put(id, new UserSummaryData(id, username, avatar, bio));
            }
        }
        return result;
    }
}
