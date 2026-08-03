package com.ulticode.modules.websocket.port.adapter;

import com.ulticode.modules.notification.port.NotificationPushPort;
import com.ulticode.modules.websocket.constants.WebSocketConstants;
import com.ulticode.app.api.dto.NotificationPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ulticode.modules.websocket.broadcast.WebSocketBroadcastBridge;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Post-Candidate-4: the adapter now owns a direct {@link SimpMessagingTemplate}
 * call. Test pins the user destination + payload + best-effort contract.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketNotificationPushAdapter")
class WebSocketNotificationPushAdapterTest {

    @Mock
    private WebSocketBroadcastBridge broadcastBridge;

    @InjectMocks
    private WebSocketNotificationPushAdapter adapter;

    @Test
    @DisplayName("pushToUser sends to /queue/notification with the given userId and payload")
    void pushToUser_sendsToUserQueueNotification() {
        NotificationPayload payload = NotificationPayload.system("n-1", "Title", "Body");
        adapter.pushToUser("u-1", payload);
        verify(broadcastBridge).sendToUser(
                "u-1", WebSocketConstants.USER_QUEUE_NOTIFICATION, payload);
        verifyNoMoreInteractions(broadcastBridge);
    }
}