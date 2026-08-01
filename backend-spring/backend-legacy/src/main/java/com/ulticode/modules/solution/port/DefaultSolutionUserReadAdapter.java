package com.ulticode.modules.solution.port;

import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.projection.UserReadProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Adapter implementing {@link SolutionUserReadPort} backed by
 * {@link UserReadProjection} from the user module.
 *
 * <p>Lives in the solution module (backend-legacy) as a transitional
 * adapter. When the solution family relocates to backend-app, this
 * adapter is replaced by a backend-app implementation that uses
 * UserProfileMapper + IdentityQueryService (following the follow
 * module's {@code DefaultUserReadAdapter} precedent).
 *
 * <p>P7-RELOCATE-SOLUTION-001: decouples the solution projection from
 * direct {@code user.entity.User} imports. The projection injects this
 * port instead of {@code UserReadProjection}.
 */
@Component
@RequiredArgsConstructor
public class DefaultSolutionUserReadAdapter implements SolutionUserReadPort {

    private final UserReadProjection userReadProjection;

    @Override
    public UserSummary findById(String userId) {
        if (userId == null) {
            return null;
        }
        User user = userReadProjection.findById(userId).orElse(null);
        return toSummary(user);
    }

    @Override
    public Map<String, UserSummary> findAllById(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Map<String, User> users = userReadProjection.findAllById(userIds);
        Map<String, UserSummary> result = new HashMap<>();
        for (Map.Entry<String, User> entry : users.entrySet()) {
            UserSummary summary = toSummary(entry.getValue());
            if (summary != null) {
                result.put(entry.getKey(), summary);
            }
        }
        return result;
    }

    private UserSummary toSummary(User user) {
        if (user == null) {
            return null;
        }
        String displayName = user.getName() != null ? user.getName() : user.getUsername();
        return new UserSummary(user.getId(), displayName, user.getAvatar());
    }
}
