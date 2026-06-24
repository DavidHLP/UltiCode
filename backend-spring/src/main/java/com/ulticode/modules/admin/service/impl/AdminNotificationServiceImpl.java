package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.AuditHelper;
import com.ulticode.common.util.SecurityUtil;
import com.ulticode.modules.admin.dto.AdminNotificationQueryDTO;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;
import com.ulticode.modules.admin.service.AdminNotificationService;
import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.entity.NotificationPreference;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.notification.mapper.NotificationPreferenceMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNotificationServiceImpl implements AdminNotificationService {

    private static final String SYSTEM_CATEGORY = "SYSTEM";
    private static final String MARKETING_CATEGORY = "MARKETING";
    private static final String COMMUNICATION_CATEGORY = "COMMUNICATION";

    /**
     * Categories whose delivery must respect per-user opt-out preferences
     * (ADR-004 §2.3). {@code SECURITY} and {@code SYSTEM} announcements are
     * force-delivered and bypass this filter.
     */
    private static final Set<String> PREFERENCE_GATED_CATEGORIES =
            Set.of(MARKETING_CATEGORY, COMMUNICATION_CATEGORY);

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "createdAt", "title", "type", "category", "announcementId"
    );

    private final NotificationMapper notificationMapper;
    private final NotificationPreferenceMapper preferenceMapper;
    private final UserMapper userMapper;

    @Override
    public PageResult<AdminNotificationVO> listSystemNotifications(AdminNotificationQueryDTO queryDTO) {
        int page = queryDTO.getPage() != null ? queryDTO.getPage() : 1;
        int limit = queryDTO.getLimit() != null ? queryDTO.getLimit() : 10;

        String sortBy = queryDTO.getSortBy();
        if (sortBy != null && !ALLOWED_SORT_FIELDS.contains(sortBy)) {
            sortBy = null;
        }
        String sortOrder = queryDTO.getSortOrder();

        Page<Notification> pageParam = new Page<>(page, limit);
        IPage<Notification> result = notificationMapper.selectDedupedAnnouncements(
                pageParam,
                SYSTEM_CATEGORY,
                queryDTO.getKeyword(),
                queryDTO.getType(),
                queryDTO.getAnnouncementId(),
                sortBy,
                sortOrder);

        List<AdminNotificationVO> vos = toAdminVOList(result.getRecords());
        return PageResult.of(vos, result.getTotal(), page, limit);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.CREATE_NOTIFICATION, entityType = AuditActionUtil.ENTITY_NOTIFICATION)
    public AdminNotificationVO createSystemNotification(CreateSystemNotificationRequest request) {
        String currentUserId = SecurityUtil.getCurrentUserId();
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Current user not found");
        }

        String category = request.getCategory() != null ? request.getCategory() : SYSTEM_CATEGORY;

        List<String> targetUserIds = getTargetUserIds(request.getTarget(), request.getUserIds());
        if (targetUserIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "No target users found");
        }

        // ADR-004 §2.3: respect per-user opt-out for MARKETING / COMMUNICATION.
        // SECURITY / SYSTEM announcements are force-delivered (never filtered).
        List<String> recipientIds = filterRecipientsByPreference(targetUserIds, category);
        int suppressedCount = targetUserIds.size() - recipientIds.size();
        if (suppressedCount > 0) {
            log.info("System notification '{}' category={}: {}/{} recipients suppressed by preference opt-out",
                    request.getTitle(), category, suppressedCount, targetUserIds.size());
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("createdBy", currentUserId);
        metadata.put("createdByName", currentUser.getUsername());
        metadata.put("isSystemAnnouncement", true);

        String announcementId = UUID.randomUUID().toString();
        // batchInsert uses a custom @Insert that bypasses MyBatis-Plus field-fill,
        // so created_at / updated_at must be set explicitly (both are NOT NULL).
        LocalDateTime now = LocalDateTime.now();

        List<Notification> notificationsToCreate = new ArrayList<>();
        for (String userId : recipientIds) {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(request.getType());
            notification.setCategory(category);
            notification.setTitle(request.getTitle());
            notification.setBody(request.getContent());
            notification.setLink(null);
            notification.setMetadata(metadata);
            notification.setAnnouncementId(announcementId);
            notification.setIsRead(false);
            notification.setReadAt(null);
            notification.setCreatedAt(now);
            notification.setUpdatedAt(now);
            notification.setDeleted(0);
            notificationsToCreate.add(notification);
        }

        if (!notificationsToCreate.isEmpty()) {
            notificationMapper.batchInsert(notificationsToCreate);
        }

        log.info("Created system notification '{}' for {}/{} users by admin {} (announcementId={})",
                request.getTitle(), recipientIds.size(), targetUserIds.size(), currentUserId, announcementId);

        AuditContext.setNewValues(Map.of(
            "title", request.getTitle() != null ? request.getTitle() : "",
            "targetCount", recipientIds.size(),
            "suppressedCount", suppressedCount,
            "target", request.getTarget() != null ? request.getTarget() : ""
        ));

        if (notificationsToCreate.isEmpty()) {
            // Every recipient opted out — record the announcement intent without
            // persisting any user-facing notification row.
            AuditContext.setEntityId(announcementId);
            return buildAnnouncementVo(request, category, announcementId);
        }
        Notification representative = notificationsToCreate.get(0);
        AuditContext.setEntityId(representative.getId());
        return toAdminVOForSingle(representative);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.DELETE_NOTIFICATION, entityType = AuditActionUtil.ENTITY_NOTIFICATION)
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
    @Audited(action = AuditActionUtil.UPDATE_NOTIFICATION, entityType = AuditActionUtil.ENTITY_NOTIFICATION)
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
            if (request.getType() != null) {
                updateWrapper.set(Notification::getType, request.getType());
            }
            if (request.getCategory() != null) {
                updateWrapper.set(Notification::getCategory, request.getCategory());
            }
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
            if (request.getType() != null) {
                updateWrapper.set(Notification::getType, request.getType());
            }
            if (request.getCategory() != null) {
                updateWrapper.set(Notification::getCategory, request.getCategory());
            }
            updatedCount = notificationMapper.update(null, updateWrapper);
        }

        AuditContext.setNewValues(Map.of(
            "title", request.getTitle() != null ? request.getTitle() : "",
            "type", request.getType() != null ? request.getType() : ""
        ));
        AuditContext.setEntityId(id);

        log.info("Updated system notification '{}' and {} related records", id, updatedCount);

        Notification updated = notificationMapper.selectById(id);
        return toAdminVOForSingle(updated);
    }

    // ==================== Private Helper Methods ====================

    private List<String> getTargetUserIds(String target, List<String> userIds) {
        if ("ALL".equals(target)) {
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getIsActive, true);
            wrapper.eq(User::getIsDeleted, 0);
            wrapper.select(User::getId);
            return userMapper.selectList(wrapper).stream()
                    .map(User::getId)
                    .collect(Collectors.toList());
        } else if ("USERS".equals(target)) {
            if (userIds == null || userIds.isEmpty()) {
                return Collections.emptyList();
            }
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
     * Filter the target user list by per-user notification preferences.
     *
     * <p>{@code SECURITY} and {@code SYSTEM} announcements bypass this filter
     * (always delivered). For {@code MARKETING} / {@code COMMUNICATION}, users
     * who have explicitly opted out are removed. Users without a preference row
     * are resolved against the DDL defaults — marketing=false (opt-out),
     * communication=true (opt-in) — matching {@code NotificationDispatcher} and
     * the legacy {@code NotificationDispatchServiceImpl} so admin broadcast and
     * event-driven dispatch apply identical preference semantics.
     */
    private List<String> filterRecipientsByPreference(List<String> userIds, String category) {
        if (userIds.isEmpty() || !PREFERENCE_GATED_CATEGORIES.contains(category)) {
            return userIds;
        }
        Map<String, NotificationPreference> prefById = preferenceMapper.selectList(
                        new LambdaQueryWrapper<NotificationPreference>()
                                .in(NotificationPreference::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(NotificationPreference::getUserId, Function.identity()));
        boolean marketing = MARKETING_CATEGORY.equals(category);
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

    /**
     * Build a lightweight VO for an announcement that produced zero deliveries
     * (every recipient opted out). No row is persisted; this only gives the
     * admin a response payload and an audit anchor.
     */
    private AdminNotificationVO buildAnnouncementVo(CreateSystemNotificationRequest request,
                                                    String category, String announcementId) {
        AdminNotificationVO vo = new AdminNotificationVO();
        vo.setAnnouncementId(announcementId);
        vo.setTitle(request.getTitle());
        vo.setContent(request.getContent());
        vo.setType(request.getType());
        vo.setCategory(category);
        return vo;
    }

    private List<AdminNotificationVO> toAdminVOList(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> creatorIds = notifications.stream()
                .filter(n -> n.getMetadata() != null && n.getMetadata().get("createdBy") != null)
                .map(n -> (String) n.getMetadata().get("createdBy"))
                .collect(Collectors.toSet());

        Map<String, User> userMap = creatorIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(creatorIds).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));

        return notifications.stream()
                .map(n -> toAdminVO(n, userMap))
                .collect(Collectors.toList());
    }

    private AdminNotificationVO toAdminVOForSingle(Notification notification) {
        if (notification == null) return null;
        return toAdminVOList(Collections.singletonList(notification)).stream()
                .findFirst().orElse(null);
    }

    private AdminNotificationVO toAdminVO(Notification notification, Map<String, User> userMap) {
        if (notification == null) return null;

        AdminNotificationVO vo = new AdminNotificationVO();
        vo.setId(notification.getId());
        vo.setAnnouncementId(notification.getAnnouncementId());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getBody());
        vo.setType(notification.getType());
        vo.setCategory(notification.getCategory());
        vo.setCreatedAt(notification.getCreatedAt());

        if (notification.getMetadata() != null) {
            String creatorId = (String) notification.getMetadata().get("createdBy");
            if (creatorId != null && userMap.containsKey(creatorId)) {
                User creator = userMap.get(creatorId);
                AdminNotificationVO.CreatorInfo creatorInfo = new AdminNotificationVO.CreatorInfo();
                creatorInfo.setId(creator.getId());
                creatorInfo.setUsername(creator.getUsername());
                creatorInfo.setAvatar(creator.getAvatar());
                vo.setCreator(creatorInfo);
            }
        }

        return vo;
    }
}