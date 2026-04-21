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
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;
import com.ulticode.modules.websocket.service.RealtimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of NotificationService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final NotificationPreferenceMapper preferenceMapper;
    private final RealtimeService realtimeService;

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
        NotificationPreference preference = preferenceMapper.findByUserId(userId)
                .orElseGet(() -> createDefaultPreference(userId));
        return toPreferenceVO(preference);
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
        if (dto.getSystem() != null) {
            preference.setSystem(dto.getSystem());
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
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getUserId, userId);
        notificationMapper.delete(wrapper);
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
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot update other user's notification");
        }

        if (dto.getIsRead() != null && dto.getIsRead() && !notification.getIsRead()) {
            notification.setIsRead(true);
            notification.setReadAt(LocalDateTime.now());
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
        if (!notification.getUserId().equals(userId)) {
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

        // WebSocket push (fire-and-forget per D-11)
        try {
            realtimeService.sendNotification(userId,
                NotificationPayload.of(
                    notification.getId(),
                    notification.getType(),
                    notification.getTitle(),
                    notification.getBody(),
                    notification.getMetadata()
                ));
        } catch (Exception e) {
            log.warn("Failed to push notification via WebSocket for user {}: {}", userId, e.getMessage());
        }

        return toVO(notification);
    }

    // ==================== Private Helper Methods ====================

    private NotificationPreference createDefaultPreference(String userId) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(userId);
        preference.setCommunication(true);
        preference.setMarketing(true);
        preference.setSecurity(true);
        preference.setSystem(true);
        preferenceMapper.insert(preference);
        return preference;
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
        vo.setSystem(preference.getSystem());
        return vo;
    }
}
