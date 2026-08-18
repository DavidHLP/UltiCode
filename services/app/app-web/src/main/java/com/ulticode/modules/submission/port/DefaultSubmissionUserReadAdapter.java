package com.ulticode.modules.submission.port;

import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.app.userprofile.entity.UserProfile;
import com.ulticode.app.userprofile.mapper.UserProfileMapper;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Default {@link SubmissionUserReadPort} adapter.
 *
 * <p>Resolves user identity via the user-profile mapper (local MyBatis read)
 * falling back to the {@code IdentityQueryService} Dubbo RPC when the local
 * row is absent (newly registered users, cross-service lookups).
 *
 * <p>P7-RELOCATE-SUBMISSION-001: mirrors
 * {@code DefaultForumUserReadAdapter} for the submission family.
 *
 * @author ulticode
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class DefaultSubmissionUserReadAdapter implements SubmissionUserReadPort {

    @DubboReference(group = "backend-auth", check = false)
    private IdentityQueryService identityQueryService;

    private final UserProfileMapper userProfileMapper;

    @Override
    public boolean existsById(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile != null) {
            return true;
        }
        // Fallback to identity service
        if (identityQueryService != null) {
            try {
                RpcResult<List<UserIdentityDTO>> res = identityQueryService.batchGetIdentity(Set.of(userId));
                return res != null && res.success() && res.data() != null && !res.data().isEmpty();
            } catch (Exception e) {
                log.warn("Identity lookup failed for userId {}: {}", userId, e.getMessage());
            }
        }
        return false;
    }

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
                String username = usernameMap.get(id);
                if (username == null || username.isBlank()) {
                    username = profile != null && profile.getName() != null
                            ? profile.getName()
                            : id;
                }
                String name = profile != null ? profile.getName() : null;
                String avatar = profile != null ? profile.getAvatar() : null;
                result.put(id, new UserSummary(id, username, name, avatar));
            }
        }
        return result;
    }
}
