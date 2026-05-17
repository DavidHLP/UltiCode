package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditHelper;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.service.AdminNotificationService;
import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Implementation of AdminNotificationService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotificationServiceImpl implements AdminNotificationService {

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final AuditHelper auditHelper;

    @Override
    public List<AdminNotificationVO> getAllSystemNotifications() {
        // Query all notifications that are system announcements
        // We identify system announcements by metadata.isSystemAnnouncement = true
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getCategory, "SYSTEM");
        wrapper.orderByDesc(Notification::getCreatedAt);

        List<Notification> notifications = notificationMapper.selectList(wrapper);

        // Group by notification content/title to get unique announcements
        // System announcements are created once per user, so we need to deduplicate
        Map<String, Notification> uniqueAnnouncements = new LinkedHashMap<>();
        for (Notification notification : notifications) {
            // Use title + type + full created timestamp as the key to group related notifications
            String key = notification.getTitle() + "_" + notification.getType() + "_" +
                         notification.getCreatedAt();
            if (!uniqueAnnouncements.containsKey(key)) {
                uniqueAnnouncements.put(key, notification);
            }
        }

        // Convert to VO with creator information
        return uniqueAnnouncements.values().stream()
                .map(this::toAdminVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AdminNotificationVO createSystemNotification(CreateSystemNotificationRequest request) {
        String currentUserId = SecurityUtil.getCurrentUserId();
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Current user not found");
        }

        // Get target users
        List<String> targetUserIds = getTargetUserIds(request.getTarget(), request.getUserIds());
        if (targetUserIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "No target users found");
        }

        // Create metadata with creator information
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("createdBy", currentUserId);
        metadata.put("createdByName", currentUser.getUsername());
        metadata.put("isSystemAnnouncement", true);

        // Create notifications for all target users
        List<Notification> notificationsToCreate = new ArrayList<>();
        for (String userId : targetUserIds) {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(request.getType());
            notification.setCategory(request.getCategory());
            notification.setTitle(request.getTitle());
            notification.setBody(request.getContent());
            notification.setLink(null);
            notification.setMetadata(metadata);
            notification.setIsRead(false);
            notification.setReadAt(null);
            notificationsToCreate.add(notification);
        }

        // Batch insert
        if (!notificationsToCreate.isEmpty()) {
            notificationMapper.batchInsert(notificationsToCreate);
        }

        log.info("Created system notification '{}' for {} users by admin {}",
                request.getTitle(), targetUserIds.size(), currentUserId);

        auditHelper.log(
            AuditActionUtil.CREATE_NOTIFICATION,
            AuditActionUtil.ENTITY_NOTIFICATION,
            notificationsToCreate.get(0).getId(),
            null,
            Map.of("title", Objects.requireNonNullElse(request.getTitle(), ""), "targetCount", targetUserIds.size(), "target", Objects.requireNonNullElse(request.getTarget(), ""))
        );

        // Return the first created notification as representative
        Notification representative = notificationsToCreate.get(0);
        return toAdminVO(representative);
    }

    @Override
    @Transactional
    public void deleteNotification(String id) {
        // Check if notification exists
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Notification not found");
        }

        // For system announcements, we need to delete all related notifications
        // with the same title, type, and creation date
        LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Notification::getTitle, notification.getTitle());
        wrapper.eq(Notification::getType, notification.getType());
        wrapper.eq(Notification::getCategory, "SYSTEM");

        // Also match by creation date to be more specific
        if (notification.getCreatedAt() != null) {
            wrapper.eq(Notification::getCreatedAt, notification.getCreatedAt());
        }

        auditHelper.log(
            AuditActionUtil.DELETE_NOTIFICATION,
            AuditActionUtil.ENTITY_NOTIFICATION,
            id,
            Map.of("title", Objects.requireNonNullElse(notification.getTitle(), ""), "type", Objects.requireNonNullElse(notification.getType(), "")),
            null
        );

        int deletedCount = notificationMapper.delete(wrapper);
        log.info("Deleted system notification '{}' and {} related records", id, deletedCount);
    }

    // ==================== Private Helper Methods ====================

    /**
     * Get target user IDs based on target type.
     */
    private List<String> getTargetUserIds(String target, List<String> userIds) {
        if ("ALL".equals(target)) {
            // Get all active, non-deleted users
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getIsActive, true);
            wrapper.eq(User::getIsDeleted, 0);
            wrapper.select(User::getId);
            return userMapper.selectList(wrapper).stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
        } else if ("USERS".equals(target)) {
            // Validate provided user IDs
            if (userIds == null || userIds.isEmpty()) {
                return Collections.emptyList();
            }
            // Filter to only valid user IDs
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(User::getId, userIds);
            wrapper.eq(User::getIsDeleted, 0);
            return userMapper.selectList(wrapper).stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Convert Notification entity to AdminNotificationVO.
     */
    private AdminNotificationVO toAdminVO(Notification notification) {
        if (notification == null) {
            return null;
        }

        AdminNotificationVO vo = new AdminNotificationVO();
        vo.setId(notification.getId());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getBody());
        vo.setType(notification.getType());
        vo.setCategory(notification.getCategory());
        vo.setCreatedAt(notification.getCreatedAt());

        // Get creator information from metadata
        if (notification.getMetadata() != null) {
            String creatorId = (String) notification.getMetadata().get("createdBy");
            if (creatorId != null) {
                User creator = userMapper.selectById(creatorId);
                if (creator != null) {
                    AdminNotificationVO.CreatorInfo creatorInfo = new AdminNotificationVO.CreatorInfo();
                    creatorInfo.setId(creator.getId());
                    creatorInfo.setUsername(creator.getUsername());
                    creatorInfo.setAvatar(creator.getAvatar());
                    vo.setCreator(creatorInfo);
                }
            }
        }

        return vo;
    }
}
