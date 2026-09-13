package com.ulticode.modules.solution.port;

import com.ulticode.app.user.port.UserDirectoryProjection;
import com.ulticode.app.user.port.UserSummaryView;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link SolutionUserReadPort} backed by the App-owned
 * directory projection.
 */
@Component
@RequiredArgsConstructor
public class DefaultSolutionUserReadAdapter implements SolutionUserReadPort {

    private final UserDirectoryProjection userDirectoryProjection;

    @Override
    public UserSummary findById(String userId) {
        return toSummary(userDirectoryProjection.selectById(userId));
    }

    @Override
    public Map<String, UserSummary> findAllById(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userDirectoryProjection.selectByIds(userIds).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> toSummary(entry.getValue()),
                        (first, ignored) -> first,
                        LinkedHashMap::new));
    }

    private UserSummary toSummary(UserSummaryView view) {
        if (view == null) {
            return null;
        }
        String displayName = view.name() == null ? view.username() : view.name();
        return new UserSummary(view.id(), view.username(), displayName, view.avatar());
    }
}
