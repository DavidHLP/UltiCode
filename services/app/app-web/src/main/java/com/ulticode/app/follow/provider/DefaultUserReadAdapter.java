package com.ulticode.app.follow.provider;

import com.ulticode.app.user.port.UserDirectoryProjection;
import com.ulticode.app.user.port.UserSummaryView;
import com.ulticode.modules.follow.port.UserReadPort;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Adapter backing UserReadPort via the App-owned directory projection. */
@Component
@RequiredArgsConstructor
public class DefaultUserReadAdapter implements UserReadPort {

    private final UserDirectoryProjection userDirectoryProjection;

    @Override
    public boolean exists(String userId) {
        return userId != null && !userId.isBlank()
                && userDirectoryProjection.selectById(userId) != null;
    }

    @Override
    public UserSummaryData findById(String userId) {
        return toSummary(userDirectoryProjection.selectById(userId));
    }

    @Override
    public Map<String, UserSummaryData> findByIds(Collection<String> userIds) {
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

    private UserSummaryData toSummary(UserSummaryView view) {
        return view == null ? null
                : new UserSummaryData(view.id(), view.username(), view.avatar(), view.bio());
    }
}
