package com.ulticode.notification.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.notification.api.dto.BadgeEarnedPayload;
import com.ulticode.notification.api.dto.NotificationPayload;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisNotificationBroadcastAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void publishesGenericNotificationEnvelope() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisNotificationBroadcastAdapter adapter = adapter(redis);

        adapter.sendToUser("u1", NotificationPayload.system("n1", "Title", "Body"));

        Map<String, Object> envelope = published(redis);
        assertThat(envelope).containsEntry("type", "USER")
                .containsEntry("userId", "u1")
                .containsEntry("kind", "notification")
                .containsEntry("destination", "/queue/notification");
    }

    @Test
    void publishesTypedBadgeEnvelope() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        RedisNotificationBroadcastAdapter adapter = adapter(redis);

        adapter.sendBadgeToUser("u1", BadgeEarnedPayload.of(
                "badge", "Badge", "Description", null, "gold", "u1"));

        assertThat(published(redis)).containsEntry("kind", "badge_earned");
    }

    @Test
    void rejectsInvalidBroadcastIdentity() {
        RedisNotificationBroadcastAdapter adapter = adapter(mock(StringRedisTemplate.class));

        assertThatThrownBy(() -> adapter.sendToUser("", NotificationPayload.system("n1", "T", "B")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RedisNotificationBroadcastAdapter adapter(StringRedisTemplate redis) {
        RedisNotificationBroadcastAdapter adapter =
                new RedisNotificationBroadcastAdapter(redis, objectMapper);
        ReflectionTestUtils.setField(adapter, "channel", "test:ws:broadcast");
        return adapter;
    }

    private Map<String, Object> published(StringRedisTemplate redis) throws Exception {
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq("test:ws:broadcast"), captor.capture());
        return objectMapper.readValue(captor.getValue(), new TypeReference<>() { });
    }
}
