package com.ulticode.modules.notification.adapter;

import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.notification.dispatcher.AnnouncementBroadcaster;
import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.entity.enums.NotificationCategory;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.notification.port.NotificationAdministrationWritePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZoneOffset;

/**
 * Notification-side implementation of {@link NotificationAdministrationWritePort}.
 *
 * <p>Uses the notification-owned broadcaster and mapper to create, update,
 * and delete system notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultNotificationAdministrationWriteAdapter implements NotificationAdministrationWritePort {

    private final AnnouncementBroadcaster broadcaster;
    private final NotificationMapper notificationMapper;
    private final Clock clock;

    @Override
    @Transactional
    public NotificationAdminViewDTO createNotification(CreateNotificationCommand command) {
        String target = command.target() == null ? "" : command.target().trim().toUpperCase();
        if (!"ALL".equals(target) && !"USERS".equals(target)) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "Notification target must be ALL or USERS");
        }
        if ("USERS".equals(target) && (command.userIds() == null || command.userIds().isEmpty())) {
            throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                    "userIds are required when target is USERS");
        }
        NotificationCategory category = NotificationCategory.SYSTEM;
        if (command.category() != null && !command.category().isBlank()) {
            try {
                category = NotificationCategory.valueOf(command.category().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        AnnouncementBroadcaster.Outcome outcome;
        try {
            outcome = broadcaster.broadcast(
                    command.title(),
                    command.content(),
                    command.type(),
                    category,
                    target,
                    command.userIds(),
                    java.util.Map.of(
                            "createdBy", command.actor().actorId(),
                            "isSystemAnnouncement", true),
                    null
            );
        } catch (IllegalArgumentException exception) {
            if ("No target users found".equals(exception.getMessage())) {
                throw new BusinessException(BaseErrorCode.BAD_REQUEST, exception.getMessage());
            }
            throw exception;
        }

        long nowEpochMs = clock.instant().toEpochMilli();
        return new NotificationAdminViewDTO(
                outcome.representativeId() != null ? outcome.representativeId() : "",
                outcome.announcementId(),
                command.title(),
                command.type(),
                category.name(),
                nowEpochMs
        );
    }

    @Override
    @Transactional
    public void deleteNotification(DeleteNotificationCommand command) {
        Notification n = notificationMapper.selectById(command.notificationId());
        if (n == null || n.getAnnouncementId() == null || n.getAnnouncementId().isBlank()) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND,
                    "System notification not found: " + command.notificationId());
        }
        notificationMapper.softDeleteAnnouncement(command.notificationId(), n.getAnnouncementId());
        log.info("Notification announcement deleted: {}", command.notificationId());
    }

    @Override
    @Transactional
    public NotificationAdminViewDTO updateNotification(UpdateNotificationCommand command) {
        Notification n = notificationMapper.selectById(command.notificationId());
        if (n == null || n.getAnnouncementId() == null || n.getAnnouncementId().isBlank()) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND,
                    "System notification not found: " + command.notificationId());
        }
        String newCategory = null;
        if (command.category() != null && !command.category().isBlank()) {
            try {
                newCategory = NotificationCategory.valueOf(command.category().trim().toUpperCase()).name();
            } catch (IllegalArgumentException exception) {
                throw new BusinessException(BaseErrorCode.BAD_REQUEST,
                        "Invalid notification category: " + command.category());
            }
        }
        String newType = command.type() != null && !command.type().isBlank()
                ? command.type() : null;
        notificationMapper.updateAnnouncement(
                n.getId(), n.getAnnouncementId(), n.getCategory(),
                command.title(), command.content(), newType, newCategory);

        long epochMs = n.getCreatedAt() != null
                ? n.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli()
                : clock.instant().toEpochMilli();

        return new NotificationAdminViewDTO(
                n.getId(),
                n.getAnnouncementId(),
                command.title(),
                newType != null ? newType : n.getType(),
                newCategory != null ? newCategory : n.getCategory(),
                epochMs);

    }
}
