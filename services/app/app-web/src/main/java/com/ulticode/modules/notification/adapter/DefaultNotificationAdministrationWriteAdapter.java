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
 * App-side implementation of {@link NotificationAdministrationWritePort}.
 *
 * <p>Uses {@link AnnouncementBroadcaster} and {@link NotificationMapper} in {@code backend-app}
 * to create, update, and delete system notifications directly without relying on {@code backend-legacy}.
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
        NotificationCategory category = NotificationCategory.SYSTEM;
        if (command.category() != null && !command.category().isBlank()) {
            try {
                category = NotificationCategory.valueOf(command.category().trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        AnnouncementBroadcaster.Outcome outcome = broadcaster.broadcast(
                command.title(),
                command.content(),
                command.type(),
                category,
                command.target(),
                command.userIds(),
                java.util.Collections.emptyMap(),
                null
        );

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
        if (n == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Notification not found: " + command.notificationId());
        }
        notificationMapper.deleteById(command.notificationId());
        log.info("Notification deleted: {}", command.notificationId());
    }

    @Override
    @Transactional
    public NotificationAdminViewDTO updateNotification(UpdateNotificationCommand command) {
        Notification n = notificationMapper.selectById(command.notificationId());
        if (n == null) {
            throw new BusinessException(BaseErrorCode.NOT_FOUND, "Notification not found: " + command.notificationId());
        }
        if (command.title() != null && !command.title().isBlank()) {
            n.setTitle(command.title());
        }
        if (command.content() != null && !command.content().isBlank()) {
            n.setBody(command.content());
        }
        notificationMapper.updateById(n);

        long epochMs = n.getCreatedAt() != null
                ? n.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli()
                : clock.instant().toEpochMilli();

        return new NotificationAdminViewDTO(
                n.getId(),
                null,
                n.getTitle() != null ? n.getTitle() : "",
                n.getType() != null ? n.getType() : "",
                n.getCategory() != null ? n.getCategory() : "",
                epochMs
        );
    }
}
