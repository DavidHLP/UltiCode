package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.notification.port.NotificationPushPort;
import com.ulticode.modules.websocket.notification.dto.NotificationPayload;
import com.ulticode.modules.websocket.service.RealtimeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Unit tests for {@link WebSocketNotificationPushAdapter}.
 *
 * <p>The adapter's only contract is to delegate {@code pushToUser} to
 * {@code RealtimeService.sendNotification} with the same arguments. After
 * extraction the notification module depends on this single method and on
 * no other producer-side machinery — this test pins that one-hop delegation
 * so a future swap to a non-STOMP transport (SSE / FCM) cannot silently
 * widen the surface.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketNotificationPushAdapter")
class WebSocketNotificationPushAdapterTest {

    @Mock
    private RealtimeService realtimeService;

    @InjectMocks
    private WebSocketNotificationPushAdapter adapter;

    @Test
    @DisplayName("pushToUser delegates to RealtimeService.sendNotification with same userId + payload")
    void pushToUser_delegatesToRealtimeService() {
        NotificationPayload payload = NotificationPayload.system("n-1", "Title", "Body");
        String userId = "u-1";

        adapter.pushToUser(userId, payload);

        verify(realtimeService).sendNotification(userId, payload);
        verifyNoMoreInteractions(realtimeService);
    }
}