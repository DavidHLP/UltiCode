package com.ulticode.modules.notification.service.impl;

import com.ulticode.modules.notification.entity.Notification;
import com.ulticode.modules.notification.mapper.NotificationMapper;
import com.ulticode.modules.notification.mapper.NotificationPreferenceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationMapper notificationMapper;

    @Mock
    private NotificationPreferenceMapper preferenceMapper;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(
                Clock.systemDefaultZone(), notificationMapper, preferenceMapper);
    }

    @Test
    @DisplayName("createNotificationRowOnly persists the row with the given fields")
    void createNotificationRowOnly_persistsRow() {
        String userId = "user-123";
        String type = "achievement";
        String category = "SYSTEM";
        String title = "Achievement Earned";
        String body = "You earned a badge";
        String link = "/achievements";

        when(notificationMapper.insert(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId("notif-456");
            return 1;
        });

        var result = notificationService.createNotificationRowOnly(
                userId, type, category, title, body, link, Map.of());

        assertThat(result.getId()).isEqualTo("notif-456");
        assertThat(result.getType()).isEqualTo(type);
        assertThat(result.getCategory()).isEqualTo(category);
        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    @DisplayName("createNotificationRowOnly writes a row even when body/link are null")
    void createNotificationRowOnly_toleratesNullBodyAndLink() {
        when(notificationMapper.insert(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId("notif-789");
            return 1;
        });

        var result = notificationService.createNotificationRowOnly(
                "user-123", "test", "test", "title", null, null, Map.of());

        assertThat(result.getId()).isEqualTo("notif-789");
        verify(notificationMapper).insert(any(Notification.class));
    }
}
