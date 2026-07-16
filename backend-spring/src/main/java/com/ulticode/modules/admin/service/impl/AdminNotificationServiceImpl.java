package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.util.PartialUpdate;
import com.ulticode.modules.admin.dto.AdminNotificationQueryDTO;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;
import com.ulticode.modules.admin.projection.AdminNotificationProjection;
import com.ulticode.modules.admin.service.AdminNotificationService;
import com.ulticode.modules.notification.dispatcher.AnnouncementBroadcaster;
import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Write state machine for admin system-notification management &mdash;
 * ADR-0011 Stage 4 deepening.
 *
 * <p>Every read-side concern (paginated list read with sort-field whitelist,
 * batch creator User enrichment, three {@code toAdminVO} overloads, the
 * {@code buildAnnouncementVo} helper) moved behind
 * {@link AdminNotificationProjection}. This service keeps the write state
 * machine only: create / update / soft-delete system announcements, plus the
 * preference-gated recipient resolution for the broadcast path
 * (ADR-004 &sect;2.3). Write paths that return {@code AdminNotificationVO}
 * ({@code createSystemNotification}, {@code updateSystemNotification}) call
 * {@link AdminNotificationProjection#toAdminVO} so the controller contract
 * is unchanged &mdash; the shape rule simply no longer lives here.
 *
 * <p>Cross-module read access ({@code UserMapper} for the {@code creator}
 * field on the VO) moved to the projection. The remaining {@code UserMapper}
 * usage here is for legitimate write-path concerns (target-user resolution
 * for the broadcast, current-admin resolution for the audit anchor) &mdash;
 * those are not projection concerns.
 *
 * <p>Mirrors the {@code AdminContestServiceImpl} (Stage 3) shape: the
 * service keeps its {@code listXxx} read method as a thin delegator to the
 * projection, and write methods that need a VO call the projection's
 * {@code toXxxVO} helper to avoid duplicating the shape rule.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotificationServiceImpl implements AdminNotificationService {

    private static final String SYSTEM_CATEGORY = "SYSTEM";

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final Clock clock;
    private final AdminNotificationProjection adminNotificationProjection;
    private final CurrentUserProvider currentUserProvider;
    /**
     * Architecture-review candidate #4: admin announcement fan-out
     * (target resolution, preference filtering, batch row insert)
     * concentrates behind {@link AnnouncementBroadcaster}. The admin
     * service keeps the audit hook, edit/delete state machine, and
     * the producer-side validation.
     */
    private final AnnouncementBroadcaster announcementBroadcaster;

    @Override
    public PageResult<AdminNotificationVO> listSystemNotifications(AdminNotificationQueryDTO queryDTO) {
        return adminNotificationProjection.getSystemNotifications(queryDTO);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.CREATE_NOTIFICATION, entityType = AuditVocabulary.ENTITY_NOTIFICATION)
    public AdminNotificationVO createSystemNotification(CreateSystemNotificationRequest request) {
        String currentUserId = currentUserProvider.getCurrentUserId();
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Current user not found");
        }

        String category = request.getCategory() != null ? request.getCategory() : SYSTEM_CATEGORY;
        NotificationCategory notificationCategory = parseCategory(category);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("createdBy", currentUserId);
        metadata.put("createdByName", currentUser.getUsername());
        metadata.put("isSystemAnnouncement", true);

        // Architecture-review candidate #4: the recipient-resolution,
        // preference-filter, and batch-row-insert mechanics now live
        // behind the AnnouncementBroadcaster seam. The admin service
        // keeps the audit anchor + producer-side validation.
        AnnouncementBroadcaster.Outcome outcome;
        try {
            outcome = announcementBroadcaster.broadcast(
                    request.getTitle(),
                    request.getContent(),
                    request.getType(),
                    notificationCategory,
                    request.getTarget(),
                    request.getUserIds(),
                    metadata,
                    /* existingAnnouncementId */ null);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }

        log.info("Created system notification '{}' for {}/{} users by admin {} (announcementId={})",
                request.getTitle(), outcome.delivered(), outcome.totalTargets(),
                currentUserId, outcome.announcementId());

        AuditContext.setNewValues(Map.of(
            "title", request.getTitle() != null ? request.getTitle() : "",
            "targetCount", outcome.delivered(),
            "suppressedCount", outcome.suppressed(),
            "target", request.getTarget() != null ? request.getTarget() : ""
        ));

        if (outcome.delivered() == 0) {
            // Every recipient opted out — record the announcement intent without
            // persisting any user-facing notification row.
            AuditContext.setEntityId(outcome.announcementId());
            return adminNotificationProjection.buildAnnouncementVO(request, category, outcome.announcementId());
        }
        AuditContext.setEntityId(outcome.representativeId());
        Notification representative = notificationMapper.selectById(outcome.representativeId());
        if (representative == null) {
            // Defensive: broadcaster promised a representative id but the
            // row vanished between insert and re-read. Fall back to the
            // announcement-shaped VO so callers still get a well-formed
            // response.
            return adminNotificationProjection.buildAnnouncementVO(request, category, outcome.announcementId());
        }
        return adminNotificationProjection.toAdminVO(representative);
    }

    private static NotificationCategory parseCategory(String wire) {
        if (wire == null || wire.isBlank()) {
            return NotificationCategory.SYSTEM;
        }
        try {
            return NotificationCategory.valueOf(wire);
        } catch (IllegalArgumentException ex) {
            // Legacy category values (SECURITY / SYSTEM / MARKETING / COMMUNICATION
            // as plain strings) already match the enum names; any other wire value
            // is treated as SYSTEM so unknown categories still surface.
            return NotificationCategory.SYSTEM;
        }
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.DELETE_NOTIFICATION, entityType = AuditVocabulary.ENTITY_NOTIFICATION)
    public void deleteNotification(String id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Notification not found");
        }

        AuditContext.setOldValues(Map.of(
            "title", notification.getTitle() != null ? notification.getTitle() : "",
            "type", notification.getType() != null ? notification.getType() : ""
        ));

        int deletedCount;
        if (notification.getAnnouncementId() != null) {
            deletedCount = notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                    .eq(Notification::getAnnouncementId, notification.getAnnouncementId())
                    .eq(Notification::getCategory, SYSTEM_CATEGORY));
        } else {
            LambdaQueryWrapper<Notification> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Notification::getTitle, notification.getTitle());
            wrapper.eq(Notification::getType, notification.getType());
            wrapper.eq(Notification::getCategory, SYSTEM_CATEGORY);
            if (notification.getCreatedAt() != null) {
                wrapper.eq(Notification::getCreatedAt, notification.getCreatedAt());
            }
            deletedCount = notificationMapper.delete(wrapper);
        }

        log.info("Deleted system notification '{}' and {} related records", id, deletedCount);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UPDATE_NOTIFICATION, entityType = AuditVocabulary.ENTITY_NOTIFICATION)
    public AdminNotificationVO updateSystemNotification(String id, UpdateSystemNotificationRequest request) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Notification not found");
        }

        AuditContext.setOldValues(Map.of(
            "title", notification.getTitle() != null ? notification.getTitle() : "",
            "type", notification.getType() != null ? notification.getType() : ""
        ));

        int updatedCount;
        if (notification.getAnnouncementId() != null) {
            LambdaUpdateWrapper<Notification> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Notification::getAnnouncementId, notification.getAnnouncementId())
                    .eq(Notification::getCategory, SYSTEM_CATEGORY)
                    .set(Notification::getTitle, request.getTitle())
                    .set(Notification::getBody, request.getContent());
            PartialUpdate.setIfPresentTextWrapper(updateWrapper, request,
                    UpdateSystemNotificationRequest::getType, Notification::getType);
            PartialUpdate.setIfPresentTextWrapper(updateWrapper, request,
                    UpdateSystemNotificationRequest::getCategory, Notification::getCategory);
            updatedCount = notificationMapper.update(null, updateWrapper);
        } else {
            LambdaUpdateWrapper<Notification> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Notification::getTitle, notification.getTitle())
                    .eq(Notification::getType, notification.getType())
                    .eq(Notification::getCategory, SYSTEM_CATEGORY)
                    .set(Notification::getTitle, request.getTitle())
                    .set(Notification::getBody, request.getContent());
            if (notification.getCreatedAt() != null) {
                updateWrapper.eq(Notification::getCreatedAt, notification.getCreatedAt());
            }
            PartialUpdate.setIfPresentTextWrapper(updateWrapper, request,
                    UpdateSystemNotificationRequest::getType, Notification::getType);
            PartialUpdate.setIfPresentTextWrapper(updateWrapper, request,
                    UpdateSystemNotificationRequest::getCategory, Notification::getCategory);
            updatedCount = notificationMapper.update(null, updateWrapper);
        }

        AuditContext.setNewValues(Map.of(
            "title", request.getTitle() != null ? request.getTitle() : "",
            "type", request.getType() != null ? request.getType() : ""
        ));
        AuditContext.setEntityId(id);

        log.info("Updated system notification '{}' and {} related records", id, updatedCount);

        Notification updated = notificationMapper.selectById(id);
        return adminNotificationProjection.toAdminVO(updated);
    }

    // Architecture-review candidate #4: target resolution, preference
    // filtering, and batch row insert moved behind AnnouncementBroadcaster.
    // The admin service keeps only the audit anchor, edit/delete state
    // machine, and producer-side validation.
}
