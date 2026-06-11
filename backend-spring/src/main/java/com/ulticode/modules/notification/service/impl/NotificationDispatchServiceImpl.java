package com.ulticode.modules.notification.service.impl;

import com.ulticode.modules.notification.dto.NotificationVO;
import com.ulticode.modules.notification.entity.NotificationPreference;
import com.ulticode.modules.notification.mapper.NotificationPreferenceMapper;
import com.ulticode.modules.notification.service.NotificationDispatchService;
import com.ulticode.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link NotificationDispatchService}.
 *
 * <p>Maps a notification's {@code category} to the corresponding preference
 * flag. Unknown categories fall through as enabled. When the user has no
 * preference row, the dispatch defaults to <em>enabled</em> (matches the
 * DDL defaults: communication=true, marketing=false, security=true,
 * system_enabled=true).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchServiceImpl implements NotificationDispatchService {

    private final NotificationService notificationService;
    private final NotificationPreferenceMapper preferenceMapper;

    @Override
    @Transactional
    public Optional<NotificationVO> dispatch(String userId,
                                             String type,
                                             String category,
                                             String title,
                                             String body,
                                             String link,
                                             Map<String, Object> metadata,
                                             boolean force) {
        if (!force && !isCategoryEnabled(userId, category)) {
            log.debug("Notification suppressed by preference: user={} category={}", userId, category);
            return Optional.empty();
        }
        NotificationVO vo = notificationService.createNotification(
                userId, type, category, title, body, link, metadata);
        return Optional.of(vo);
    }

    private boolean isCategoryEnabled(String userId, String category) {
        return preferenceMapper.findByUserId(userId)
                .map(p -> isEnabled(p, category))
                // No row → use DDL defaults: marketing=false, others=true.
                .orElseGet(() -> !"MARKETING".equalsIgnoreCase(category));
    }

    private boolean isEnabled(NotificationPreference p, String category) {
        if (category == null) {
            return true;
        }
        return switch (category.toUpperCase()) {
            case "COMMUNICATION" -> Boolean.TRUE.equals(p.getCommunication());
            case "MARKETING"    -> Boolean.TRUE.equals(p.getMarketing());
            case "SECURITY"     -> Boolean.TRUE.equals(p.getSecurity());
            case "SYSTEM"       -> Boolean.TRUE.equals(p.getSystemEnabled());
            // M1: unknown category is treated as opt-in (legacy behavior) but
            // emits a warning so callers can spot misclassified notifications.
            default -> {
                log.warn("Unknown notification category '{}'; treating as enabled. "
                        + "Add an explicit case to NotificationDispatchServiceImpl.isEnabled "
                        + "if a new category should respect preferences.", category);
                yield true;
            }
        };
    }
}
