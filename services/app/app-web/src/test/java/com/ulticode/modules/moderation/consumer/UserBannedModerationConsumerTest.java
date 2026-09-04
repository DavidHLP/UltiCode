package com.ulticode.modules.moderation.consumer;

import com.ulticode.modules.moderation.port.ModerationAccountPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserBannedModerationConsumerTest {

    @Mock private ModerationAccountPort accountPort;

    private UserBannedModerationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new UserBannedModerationConsumer(accountPort);
    }
    @Test
    void consumeDispatchesToAccountPort() {
        Map<String, Object> payload = Map.of(
                "userId", "u-1",
                "isBanned", true,
                "reason", "spam",
                "bannedById", "mod-1",
                "actionId", "act-1"
        );

        consumer.consume(payload);

        verify(accountPort).updateBanStatus("u-1", true, "spam", "mod-1", "act-1");
    }

    @Test
    void consumeRejectsNonBooleanIsBanned() {
        Map<String, Object> payload = Map.of(
                "userId", "u-1",
                "isBanned", "true",
                "reason", "spam",
                "bannedById", "mod-1",
                "actionId", "act-1"
        );

        assertThatThrownBy(() -> consumer.consume(payload))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing or invalid boolean UserBanned field: isBanned");
    }
    @Test
    void consumeRejectsMissingPayloadOrRequiredFields() {
        assertThatThrownBy(() -> consumer.consume(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> consumer.consume(Map.of("isBanned", true)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
