package com.ulticode.modules.notification.adapter;

import com.ulticode.app.api.command.CreateNotificationCommand;
import com.ulticode.app.api.command.DeleteNotificationCommand;
import com.ulticode.app.api.command.UpdateNotificationCommand;
import com.ulticode.app.api.dto.NotificationAdminViewDTO;
import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;
import com.ulticode.modules.admin.service.AdminNotificationService;
import com.ulticode.modules.notification.port.NotificationAdministrationWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class LegacyNotificationWriteAdapter implements NotificationAdministrationWritePort {

    private final AdminNotificationService notificationService;

    @Override
    public NotificationAdminViewDTO createNotification(CreateNotificationCommand command) {
        CreateSystemNotificationRequest request = new CreateSystemNotificationRequest();
        request.setTitle(command.title());
        request.setContent(command.content());
        request.setType(command.type());
        request.setCategory(command.category());
        request.setTarget(command.target());
        request.setUserIds(command.userIds());

        AdminNotificationVO vo = notificationService.createSystemNotification(request);
        return toDto(vo);
    }

    @Override
    public void deleteNotification(DeleteNotificationCommand command) {
        notificationService.deleteNotification(command.notificationId());
    }

    @Override
    public NotificationAdminViewDTO updateNotification(UpdateNotificationCommand command) {
        UpdateSystemNotificationRequest request = new UpdateSystemNotificationRequest();
        request.setTitle(command.title());
        request.setContent(command.content());
        request.setType(command.type());
        request.setCategory(command.category());

        AdminNotificationVO vo = notificationService.updateSystemNotification(command.notificationId(), request);
        return toDto(vo);
    }

    private static NotificationAdminViewDTO toDto(AdminNotificationVO vo) {
        if (vo == null) {
            return null;
        }
        long epochMs = vo.getCreatedAt() != null
                ? vo.getCreatedAt().toInstant(ZoneOffset.UTC).toEpochMilli() : 0L;
        return new NotificationAdminViewDTO(
                vo.getId() != null ? vo.getId() : "",
                vo.getAnnouncementId(),
                vo.getTitle() != null ? vo.getTitle() : "",
                vo.getType() != null ? vo.getType() : "",
                vo.getCategory() != null ? vo.getCategory() : "",
                epochMs);
    }
}
