package com.ulticode.modules.notification.port.adapter;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.app.api.dto.NotificationAdminDTO;
import com.ulticode.app.api.service.NotificationAdminReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Production adapter implementing {@link NotificationAdminReadPort}.
 *
 * <p>Maps backend-app notification entities to entity-free DTOs so
 * backend-admin consumers never import notification entity or mapper
 * classes (ADMIN-008). The paginated list reuses
 * {@link NotificationMapper#selectDedupedAnnouncements}, the same
 * deduplicated system-announcement query the legacy admin panel used.
 *
 * @author ulticode
 */
@Component
@Primary
@RequiredArgsConstructor
public class DefaultNotificationAdminReadAdapter implements NotificationAdminReadPort {

    /** Default category for the admin system-announcement surface. */
    private static final String SYSTEM_CATEGORY = "SYSTEM";

    private final NotificationMapper notificationMapper;

    @Override
    public PageResult<NotificationAdminDTO> selectSystemNotifications(
            int page, int size, String keyword, String type,
            String announcementId, String sortBy, String sortOrder) {
        return selectSystemNotifications(page, size, keyword, type, null,
                announcementId, sortBy, sortOrder);
    }

    @Override
    public PageResult<NotificationAdminDTO> selectSystemNotifications(
            int page, int size, String keyword, String type, String category,
            String announcementId, String sortBy, String sortOrder) {
        String resolvedCategory = category == null || category.isBlank()
                ? SYSTEM_CATEGORY
                : category;
        IPage<Notification> result = notificationMapper.selectDedupedAnnouncements(
                new Page<>(page, size),
                resolvedCategory,
                keyword,
                type,
                announcementId,
                sortBy,
                sortOrder);
        List<NotificationAdminDTO> items = result.getRecords().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return PageResult.of(items, result.getTotal(), page, size);
    }

    @Override
    public NotificationAdminDTO selectById(String id) {
        Notification notification = notificationMapper.selectById(id);
        return notification != null ? toDTO(notification) : null;
    }

    private NotificationAdminDTO toDTO(Notification n) {
        String createdBy = null;
        if (n.getMetadata() != null && n.getMetadata().get("createdBy") instanceof String s) {
            createdBy = s;
        }
        return new NotificationAdminDTO(
                n.getId(),
                n.getAnnouncementId(),
                n.getTitle(),
                n.getBody(),
                n.getType(),
                n.getCategory(),
                n.getCreatedAt(),
                createdBy);
    }
}
