package com.ulticode.modules.moderation.port.adapter;

import com.ulticode.app.api.dto.ModerationUserInfo;
import com.ulticode.app.user.port.UserFactView;
import com.ulticode.app.user.port.UserFactsProjection;
import com.ulticode.modules.moderation.port.ModerationUserReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Production adapter for the moderation module's local
 * {@link ModerationUserReadPort} sub-interface (P7-RELOCATE).
 *
 * <p>Reads the Auth-owned {@code users} table through the App-owned
 * {@link UserFactsProjection} read seam (same shared-read precedent as the
 * other relocated user-surface projections). Implements the local sub-interface
 * on purpose: the smoke test mocks that exact type, and the sub-interface
 * inherits the app-api contract unchanged.
 *
 * <p>{@link #findByIds} crosses the owner-composed User Facts Projection once; the
 * caller ({@code DefaultModerationProjection}) already caps the id set to the
 * users referenced by one page of queue items.
 */
@Component
@RequiredArgsConstructor
public class ModerationUserReadAdapter implements ModerationUserReadPort {

    private final UserFactsProjection userFactsProjection;

    @Override
    public ModerationUserInfo findById(String userId) {
        if (userId == null) {
            return null;
        }
        UserFactView user = userFactsProjection.findById(userId);
        return user != null ? new ModerationUserInfo(user.id(), user.username()) : null;
    }

    @Override
    public Map<String, ModerationUserInfo> findByIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<String> requested = userIds.stream()
                .filter(userId -> userId != null && !userId.isBlank())
                .map(String::trim)
                .filter(userId -> !userId.isBlank())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<String, UserFactView> users = userFactsProjection.findByIds(requested);
        if (users == null) {
            users = Map.of();
        }
        Map<String, ModerationUserInfo> result = new LinkedHashMap<>();
        for (String userId : requested) {
            UserFactView user = users.get(userId);
            if (user != null) {
                result.put(user.id(), new ModerationUserInfo(user.id(), user.username()));
            }
        }
        return result;
    }
}
