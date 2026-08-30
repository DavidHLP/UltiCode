package com.ulticode.modules.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.event.inbox.ConsumerInboxMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class AdminAuditIntegrationInboxBridgeTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    @SuppressWarnings("rawtypes")
    private StreamOperations streamOperations;
    @Mock
    private ConsumerInboxMapper inboxMapper;
    @Mock
    private UuidGenerator uuidGenerator;
    @Mock
    private AdminAuditEventConsumer auditEventConsumer;

    @Test
    void poisonsAuditEventFromUnexpectedOwner() {
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(streamOperations.createGroup(anyString(), any(), anyString())).thenReturn("OK");
        when(uuidGenerator.newId()).thenReturn("poison-1");
        MapRecord<String, String, String> record = StreamRecords.mapBacked(Map.of(
                        "eventId", "audit-foreign",
                        "owner", "Notification",
                        "eventType", "AuditRecorded",
                        "payload", "{}"))
                .withStreamKey("stream:integration")
                .withId(RecordId.of("1-0"));
        doReturn(List.of(record), List.of())
                .when(streamOperations)
                .read(any(org.springframework.data.redis.connection.stream.Consumer.class),
                        any(StreamReadOptions.class), any(StreamOffset.class));
        when(inboxMapper.insertIfAbsent(anyString(), eq("Admin-Audit"),
                eq("audit-foreign"), eq("IntegrationEventPoison"), anyString())).thenReturn(1);

        AdminAuditIntegrationInboxBridge bridge = new AdminAuditIntegrationInboxBridge(
                redisTemplate,
                inboxMapper,
                new ObjectMapper(),
                uuidGenerator,
                null,
                auditEventConsumer);

        assertThat(bridge.consume()).isEqualTo(1);

        verify(inboxMapper).insertIfAbsent(anyString(), eq("Admin-Audit"),
                eq("audit-foreign"), eq("IntegrationEventPoison"), anyString());
    }
}
