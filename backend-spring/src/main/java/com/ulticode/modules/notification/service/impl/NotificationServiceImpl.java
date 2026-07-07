package com.ulticode.modules.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.notification.dto.*;
import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.entity.NotificationPreference;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.notification.mapper.NotificationPreferenceMapper;
import com.ulticode.modules.notification.port.NotificationPushPort;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Implementation of NotificationService.
 *
 * <p>Notable behaviors:
 * <ul>
 *   <li>{@link #getPreferences(String)} returns DDL defaults when no row exists
 *       and does NOT eagerly create a row (Q18/Q19 fix).</li>
 *   <li>{@link #updateNotification} handles the isRead=true/false transition
 *       bidirectionally, clearing {@code readAt} on un-mark (Q6 fix).</li>
 *   <li>{@link #clearAll(String)} batches deletes to avoid lock-wait on large
 *       users (Q17 fix).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final Clock clock;

    /** DDL defaults for {@code notification_preferences} — kept in sync with V20260602_120000. */
    private static final boolean DEFAULT_COMMUNICATION = true;
    private static final boolean DEFAULT_MARKETING = false;
    private static final boolean DEFAULT_SECURITY = true;
    private static final boolean DEFAULT_SYSTEM_ENABLED = true;

    /** Batch size for clear/delete to keep individual transactions short (Q17). */
    private static final int DELETE_BATCH_SIZE = 500;

    private final NotificationMapper notificationMapper;
    private final NotificationPreferenceMapper preferenceMapper;
    private final NotificationPushPort notificationPushPort;

    @Override
    public PageResult<NotificationVO> list(String userId, NotificationQueryDTO query) {
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);

        if (query.getType() != null && !query.getType().isEmpty()) {
            wrapper.eq(Notification::getType, query.getType());
        }
        if (query.getCategory() != null && !query.getCategory().isEmpty()) {
            wrapper.eq(Notification::getCategory, query.getCategory());
        }
        if (query.getIsRead() != null) {
            wrapper.eq(Notification::getIsRead, query.getIsRead());
        }

        wrapper.orderByDesc(Notification::getCreatedAt);

        Page<Notification> page = new Page<>(query.getPage(), query.getLimit());
        Page<Notification> result = notificationMapper.selectPage(page, wrapper);

        List<NotificationVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), query.getPage(), query.getLimit());
    }

    @Override
    public UnreadCountVO getUnreadCount(String userId) {
        long count = notificationMapper.countUnreadByUserId(userId);
        return new UnreadCountVO(count);
    }

    @Override
    public NotificationPreferenceVO getPreferences(String userId) {
        // Q18/Q19 fix: do NOT eagerly create a row on read; return DDL defaults when missing.
        return preferenceMapper.findByUserId(userId)
                .map(this::toPreferenceVO)
                .orElseGet(this::defaultPreferenceVO);
    }

    @Override
    @Transactional
    public NotificationPreferenceVO updatePreferences(String userId, UpdateNotificationPreferenceDTO dto) {
        NotificationPreference preference = preferenceMapper.findByUserId(userId)
                .orElseGet(() -> createDefaultPreference(userId));

        if (dto.getCommunication() != null) {
            preference.setCommunication(dto.getCommunication());
        }
        if (dto.getMarketing() != null) {
            preference.setMarketing(dto.getMarketing());
        }
        if (dto.getSecurity() != null) {
            preference.setSecurity(dto.getSecurity());
        }
        if (dto.getSystemEnabled() != null) {
            preference.setSystemEnabled(dto.getSystemEnabled());
        }

        if (preference.getId() == null) {
            preferenceMapper.insert(preference);
        } else {
            preferenceMapper.updateById(preference);
        }
        return toPreferenceVO(preference);
    }

    @Override
    @Transactional
    public void markAllRead(String userId) {
        notificationMapper.markAllAsRead(userId);
        log.debug("Marked all notifications as read for user {}", userId);
    }

    @Override
    @Transactional
    public void clearAll(String userId) {
        // Q17 fix: batch deletes to avoid long-running single-statement locks on
        // users with thousands of notifications.
        int total;
        do {
            LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Notification::getUserId, userId).last("LIMIT " + DELETE_BATCH_SIZE);
            total = notificationMapper.delete(wrapper);
        } while (total >= DELETE_BATCH_SIZE);
        log.debug("Cleared all notifications for user {}", userId);
    }

    @Override
    @Transactional
    public NotificationVO updateNotification(String userId, String notificationId, UpdateNotificationDTO dto) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Notification not found");
        }

        // Verify ownership
        if (!Objects.equals(notification.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot update other user's notification");
        }

        // Q6 fix: handle isRead=true (mark) and isRead=false (un-mark) bidirectionally.
        if (dto.getIsRead() != null) {
            boolean newRead = dto.getIsRead();
            if (newRead && !notification.getIsRead()) {
                notification.setIsRead(true);
                notification.setReadAt(LocalDateTime.now(clock));
            } else if (!newRead && notification.getIsRead()) {
                notification.setIsRead(false);
                notification.setReadAt(null);
            }
            notificationMapper.updateById(notification);
        }

        return toVO(notification);
    }

    @Override
    @Transactional
    public void deleteNotification(String userId, String notificationId) {
        Notification notification = notificationMapper.selectById(notificationId);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Notification not found");
        }

        // Verify ownership
        if (!Objects.equals(notification.getUserId(), userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot delete other user's notification");
        }

        notificationMapper.deleteById(notificationId);
        log.debug("Deleted notification {} for user {}", notificationId, userId);
    }

    @Override
    @Transactional
    public NotificationVO createNotification(String userId, String type, String category,
                                              String title, String body, String link,
                                              Map<String, Object> metadata) {
        // Legacy path (ADR-004 M4b): the row insert is shared with the new
        // InAppNotificationChannel; only this legacy wrapper also mirrors to
        // the WebSocket USER_QUEUE_NOTIFICATIONS topic. The new path pushes
        // via WebSocketNotificationChannel so failure isolation works per-channel.
        NotificationVO vo = createNotificationRowOnly(userId, type, category, title, body, link, metadata);

        // WebSocket push (fire-and-forget per D-11). The port swallows
        // exceptions per its contract; the defensive try/catch guards against
        // a future adapter that does not.
        try {
            notificationPushPort.pushToUser(userId,
                NotificationPayload.of(
                    vo.getId(),
                    vo.getType(),
                    vo.getTitle(),
                    vo.getBody(),
                    vo.getMetadata()
                ));
        } catch (Exception e) {
            log.warn("Failed to push notification via WebSocket for user {}: {}", userId, e.getMessage());
        }

        return vo;
    }

    @Override
    @Transactional
    public NotificationVO createNotificationRowOnly(String userId, String type, String category,
                                                     String title, String body, String link,
                                                     Map<String, Object> metadata) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setCategory(category);
        notification.setTitle(title);
        notification.setBody(body);
        notification.setLink(link);
        notification.setIsRead(false);
        notification.setMetadata(metadata);

        notificationMapper.insert(notification);
        log.debug("Created notification {} for user {}", notification.getId(), userId);
        return toVO(notification);
    }

    // ==================== Private Helper Methods ====================

    private NotificationPreference createDefaultPreference(String userId) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);
        preference.setCommunication(DEFAULT_COMMUNICATION);
        preference.setMarketing(DEFAULT_MARKETING);
        preference.setSecurity(DEFAULT_SECURITY);
        preference.setSystemEnabled(DEFAULT_SYSTEM_ENABLED);
        preferenceMapper.insert(preference);
        return preference;
    }

    private NotificationPreferenceVO defaultPreferenceVO() {
        return new NotificationPreferenceVO(
                DEFAULT_COMMUNICATION, DEFAULT_MARKETING,
                DEFAULT_SECURITY, DEFAULT_SYSTEM_ENABLED);
    }

    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setType(notification.getType());
        vo.setCategory(notification.getCategory());
        vo.setTitle(notification.getTitle());
        vo.setBody(notification.getBody());
        vo.setLink(notification.getLink());
        vo.setMetadata(notification.getMetadata());
        vo.setIsRead(notification.getIsRead());
        vo.setReadAt(notification.getReadAt());
        vo.setCreatedAt(notification.getCreatedAt());
        return vo;
    }

    private NotificationPreferenceVO toPreferenceVO(NotificationPreference preference) {
        NotificationPreferenceVO vo = new NotificationPreferenceVO();
        vo.setCommunication(preference.getCommunication());
        vo.setMarketing(preference.getMarketing());
        vo.setSecurity(preference.getSecurity());
        vo.setSystemEnabled(preference.getSystemEnabled());
        return vo;
    }
}
