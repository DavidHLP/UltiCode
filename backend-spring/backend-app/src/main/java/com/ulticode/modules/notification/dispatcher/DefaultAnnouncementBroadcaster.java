package com.ulticode.modules.notification.dispatcher;

import com.ulticode.app.api.service.UserReadPort;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.entity.NotificationPreference;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.notification.mapper.NotificationPreferenceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default {@link AnnouncementBroadcaster} implementation (architecture-review
 * candidate #4 deepening).
 *
 * <p>Owns the three-step fan-out that used to live inline in the admin
 * service: target resolution → preference filter → batch row insert.
 * The {@code SECURITY} / {@code SYSTEM} force-delivery policy (ADR-004
 * §2.3) is preserved verbatim — only the implementation moved. Channel
 * fan-out is deliberately NOT driven through {@link NotificationDispatcher}
 * for admin announcements because the architecture review marks admin
 * broadcast as a documented exception that force-delivers outside
 * {@code SystemAlertIntent}; preserving that exception is part of the
 * preservation invariant.
 *
 * <p>User resolution is provided by the {@link UserReadPort} bridge adapter
 * ({@code UserReadAdapter}) located in {@code backend-legacy}, which delegates
 * to the legacy {@code UserMapper}. This keeps {@code backend-app} free of
 * {@code com.ulticode.modules.user.*} imports.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultAnnouncementBroadcaster implements AnnouncementBroadcaster {

    /** Categories whose delivery must respect per-user opt-out preferences. */
    private static final Set<NotificationCategory> PREFERENCE_GATED = Set.of(
            NotificationCategory.MARKETING, NotificationCategory.COMMUNICATION);

    /** Wire targets understood by {@link #resolveTargets}. */
    private static final String TARGET_ALL = "ALL";
    private static final String TARGET_USERS = "USERS";

    private final NotificationMapper notificationMapper;
    private final NotificationPreferenceMapper preferenceMapper;
    private final UserReadPort userReadPort;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    @Override
    @Transactional
    public Outcome broadcast(String title,
                             String body,
                             String type,
                             NotificationCategory category,
                             String target,
                             List<String> userIds,
                             Map<String, Object> metadata,
                             String existingAnnouncementId) {
        List<String> targetIds = resolveTargets(target, userIds);
        if (targetIds.isEmpty()) {
            throw new IllegalArgumentException("No target users found");
        }
        List<String> recipientIds = filterRecipientsByPreference(targetIds, category);
        int suppressed = targetIds.size() - recipientIds.size();
        if (suppressed > 0) {
            log.info("Announcement '{}' category={}: {}/{} recipients suppressed by preference opt-out",
                    title, category, suppressed, targetIds.size());
        }

        String announcementId = existingAnnouncementId != null
                ? existingAnnouncementId
                : uuidGenerator.newId();
        LocalDateTime now = LocalDateTime.now(clock);
        List<Notification> rows = new ArrayList<>(recipientIds.size());
        for (String userId : recipientIds) {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(type);
            notification.setCategory(category.name());
            notification.setTitle(title);
            notification.setBody(body);
            notification.setLink(null);
            notification.setMetadata(metadata);
            notification.setAnnouncementId(announcementId);
            notification.setIsRead(false);
            notification.setReadAt(null);
            notification.setCreatedAt(now);
            notification.setUpdatedAt(now);
            notification.setDeleted(0);
            rows.add(notification);
        }
        if (!rows.isEmpty()) {
            notificationMapper.batchInsert(rows);
        }

        String representativeId = rows.isEmpty()
                ? announcementId
                : rows.get(0).getId();
        return new Outcome(announcementId, representativeId,
                recipientIds.size(), suppressed, targetIds.size());
    }

    @Override
    public Outcome broadcastWithChannelFanOut(String title,
                                              String body,
                                              String type,
                                              NotificationCategory category,
                                              String target,
                                              List<String> userIds,
                                              Map<String, Object> metadata,
                                              String existingAnnouncementId) {
        // Architecture-review candidate #4 ADR-004 §2.3: admin broadcast
        // is a documented exception that force-delivers outside the
        // event-driven SystemAlertIntent path. The broadcast result is
        // identical to broadcast() — the row fan-out IS the channel
        // contract for admin announcements. We deliberately do NOT
        // route through NotificationDispatcher here.
        return broadcast(title, body, type, category, target, userIds,
                metadata, existingAnnouncementId);
    }

    // -- target resolution --------------------------------------------------

    private List<String> resolveTargets(String target, List<String> userIds) {
        if (TARGET_ALL.equals(target)) {
            // "ALL" target must be resolved by the admin BFF before calling
            // this method; we cannot enumerate all active users without the
            // user domain being wired. Return empty to force broadcast() to
            // throw with a clear message.
            return Collections.emptyList();
        }
        if (TARGET_USERS.equals(target)) {
            if (userIds == null || userIds.isEmpty()) {
                return Collections.emptyList();
            }
            // Verify each userId exists and return only those found
            return userIds.stream()
                    .filter(uid -> userReadPort.findById(uid) != null)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    // -- preference gate ----------------------------------------------------

    private List<String> filterRecipientsByPreference(List<String> userIds,
                                                      NotificationCategory category) {
        if (userIds.isEmpty() || !PREFERENCE_GATED.contains(category)) {
            return userIds;
        }
        Map<String, NotificationPreference> prefById = preferenceMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<NotificationPreference>()
                                .in(NotificationPreference::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(NotificationPreference::getUserId,
                        Function.identity()));
        boolean marketing = category == NotificationCategory.MARKETING;
        return userIds.stream()
                .filter(uid -> {
                    NotificationPreference pref = prefById.get(uid);
                    if (pref == null) {
                        // DDL defaults: marketing=false, communication=true.
                        return !marketing;
                    }
                    return marketing
                            ? Boolean.TRUE.equals(pref.getMarketing())
                            : Boolean.TRUE.equals(pref.getCommunication());
                })
                .collect(Collectors.toList());
    }
}
