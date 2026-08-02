package com.ulticode.modules.notification.port;

import com.ulticode.app.api.dto.NotificationUserInfo;
import com.ulticode.app.api.service.UserReadPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * Legacy adapter implementing {@link UserReadPort} via {@link UserMapper}.
 *
 * <p>This adapter bridges the {@code UserReadPort} seam (owned by app-api) to
 * the legacy {@code UserMapper} data access. The adapter lives here in
 * {@code backend-legacy} so that {@code backend-app}'s
 * {@code DefaultAnnouncementBroadcaster} can hold a pure app-api interface
 * reference while delegating to legacy data access — zero backend-app
 * imports of {@code com.ulticode.modules.user.*}.
 *
 * <p>Pattern: same as {@code LegacyNotificationWriteAdapter} for the
 * notification write surface.
 */
@Component
@RequiredArgsConstructor
public class UserReadAdapter implements UserReadPort {

    private final UserMapper userMapper;

    @Override
    public NotificationUserInfo findById(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return null;
        }
        return new NotificationUserInfo(user.getId(), user.getUsername(), user.getEmail());
    }

    @Override
    public List<NotificationUserInfo> findByIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        return users.stream()
                .map(u -> new NotificationUserInfo(u.getId(), u.getUsername(), u.getEmail()))
                .toList();
    }
}
