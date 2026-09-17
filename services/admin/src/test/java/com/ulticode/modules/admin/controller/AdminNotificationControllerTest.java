package com.ulticode.modules.admin.controller;

import com.ulticode.modules.admin.dto.AdminNotificationVO;
import com.ulticode.modules.admin.dto.CreateSystemNotificationRequest;
import com.ulticode.modules.admin.dto.UpdateSystemNotificationRequest;
import com.ulticode.modules.admin.service.AdminNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminNotificationController")
class AdminNotificationControllerTest {

    @Mock
    private AdminNotificationService adminNotificationService;

    private AdminNotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminNotificationController(adminNotificationService);
    }

    @Test
    @DisplayName("forwards Idempotency-Key for notification writes")
    void forwardsIdempotencyKeyForWrites() {
        CreateSystemNotificationRequest createRequest = new CreateSystemNotificationRequest();
        AdminNotificationVO created = new AdminNotificationVO();
        when(adminNotificationService.createSystemNotification(createRequest, "create-retry"))
                .thenReturn(created);

        assertThat(controller.createNotification(createRequest, "create-retry").getData())
                .isSameAs(created);
        verify(adminNotificationService).createSystemNotification(createRequest, "create-retry");

        controller.deleteNotification("notification-1", "delete-retry");
        verify(adminNotificationService).deleteNotification("notification-1", "delete-retry");

        UpdateSystemNotificationRequest updateRequest = new UpdateSystemNotificationRequest();
        AdminNotificationVO updated = new AdminNotificationVO();
        when(adminNotificationService.updateSystemNotification(
                "notification-1", updateRequest, "update-retry")).thenReturn(updated);

        assertThat(controller.updateNotification(
                "notification-1", updateRequest, "update-retry").getData()).isSameAs(updated);
        verify(adminNotificationService).updateSystemNotification(
                "notification-1", updateRequest, "update-retry");
    }
}
