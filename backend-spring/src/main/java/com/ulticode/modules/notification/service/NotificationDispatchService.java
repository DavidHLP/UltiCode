package com.ulticode.modules.notification.service;

import com.ulticode.modules.notification.dto.NotificationVO;

import java.util.Map;
import java.util.Optional;

/**
 * Dispatch service for creating notifications with preference-aware filtering.
 *
 * <p>Q20 fix: every notification creator site (submission, follow, achievement,
 * contest, admin) must consult the target user's
 * {@code NotificationPreference} before inserting. When a category is
 * disabled, the dispatch is a no-op and an empty {@code Optional} is returned.
 *
 * <p>The {@code force} flag bypasses the preference check for system- and
 * admin-originated messages (security alerts, admin broadcasts) that must
 * always reach the user.
 *
 * <p><b>Deprecated since ADR-004 M4a:</b> replaced by
 * {@link com.ulticode.modules.notification.dispatcher.NotificationDispatcher}
 * + {@link com.ulticode.modules.notification.intent.NotificationIntent}. The
 * new dispatcher fans out to per-channel
 * {@link com.ulticode.modules.notification.channel.NotificationChannel}
 * beans with ledger-backed idempotency and per-channel failure isolation.
 * This service is kept for the M4c flag-off fallback path and will be
 * removed at M4d once every caller has been migrated and
 * {@code app.features.use-notification-intent} is permanently on.
 *
 * @deprecated use {@link com.ulticode.modules.notification.dispatcher.NotificationDispatcher}
 *     via a typed {@link com.ulticode.modules.notification.intent.NotificationIntent}
 *     instead.
 */
@Deprecated
public interface NotificationDispatchService {

    /**
     * Create a notification, respecting user preferences unless {@code force} is true.
     *
     * @param userId    target user id
     * @param type      notification type (COMMENT, FOLLOW, ...)
     * @param category  preference category (COMMUNICATION, MARKETING, SECURITY, SYSTEM)
     * @param force     if true, bypass the preference check
     * @return the created VO, or empty if the user has disabled this category
     */
    Optional<NotificationVO> dispatch(String userId,
                                      String type,
                                      String category,
                                      String title,
                                      String body,
                                      String link,
                                      Map<String, Object> metadata,
                                      boolean force);
}
