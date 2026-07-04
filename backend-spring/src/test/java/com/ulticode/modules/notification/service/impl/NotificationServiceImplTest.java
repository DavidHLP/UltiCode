package com.ulticode.modules.notification.service.impl;

import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.notification.mapper.NotificationPreferenceMapper;
import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.port.NotificationPushPort;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationPreferenceMapper preferenceMapper;

    @Mock
    private NotificationPushPort notificationPushPort;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(notificationMapper, preferenceMapper, notificationPushPort);
    }

    @Test
    @DisplayName("createNotification persists notification and pushes via WebSocket")
    void createNotification_persistsAndPushesWebSocket() {
        String userId = "user-123";
        String type = "achievement";
        String category = "badge_earned";
        String title = "Achievement Earned";
        String body = "You earned a badge";
        String link = "/achievements";

        when(notificationMapper.insert(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId("notif-456");
            return 1;
        });

        var result = notificationService.createNotification(userId, type, category, title, body, link, Map.of());

        assertThat(result.getId()).isEqualTo("notif-456");
        verify(notificationMapper).insert(any(Notification.class));

        ArgumentCaptor<NotificationPayload> payloadCaptor = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(notificationPushPort).pushToUser(eq(userId), payloadCaptor.capture());

        NotificationPayload payload = payloadCaptor.getValue();
        assertThat(payload.id()).isEqualTo("notif-456");
        assertThat(payload.type()).isEqualTo(type);
        assertThat(payload.title()).isEqualTo(title);
        assertThat(payload.content()).isEqualTo(body);
        assertThat(payload.event()).isEqualTo("notification");
        assertThat(payload.read()).isFalse();
    }

    @Test
    @DisplayName("createNotification does not fail when WebSocket push throws")
    void createNotification_fireAndForgetDoesNotFail() {
        String userId = "user-123";
        when(notificationMapper.insert(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId("notif-789");
            return 1;
        });
        doThrow(new RuntimeException("WebSocket unavailable")).when(notificationPushPort).pushToUser(any(), any());

        var result = notificationService.createNotification(userId, "test", "test", "title", "body", null, Map.of());

        assertThat(result.getId()).isEqualTo("notif-789");
        verify(notificationMapper).insert(any(Notification.class));
    }
}
