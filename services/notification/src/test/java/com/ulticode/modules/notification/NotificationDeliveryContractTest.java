package com.ulticode.modules.notification;

import com.ulticode.notification.api.dto.NotificationPayload;
import com.ulticode.modules.notification.channel.NotificationChannel;
import com.ulticode.modules.notification.channel.WebSocketNotificationChannel;
import com.ulticode.modules.notification.intent.NotificationIntent;
import com.ulticode.notification.websocket.NotificationBroadcastPort;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationDeliveryContractTest {

    @Test
    void intentAndChannelSeamsRemainEntityFree() {
        assertThat(NotificationIntent.class.isSealed()).isTrue();
        assertThat(Arrays.stream(NotificationIntent.class.getDeclaredMethods())
                .map(Method::getName))
                .contains("userId", "category", "intentId", "wireType", "toPushPayload");
        NotificationChannel channel = new WebSocketNotificationChannel(
                mock(NotificationBroadcastPort.class));
        assertThat(channel.channelId()).isEqualTo("websocket");
        assertThat(NotificationPayload.class.getPackageName())
                .isEqualTo("com.ulticode.notification.api.dto");
    }
}
