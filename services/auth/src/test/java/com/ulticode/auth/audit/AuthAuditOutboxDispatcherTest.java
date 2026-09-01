package com.ulticode.auth.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.event.IntegrationEventEnvelopeContract;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class AuthAuditOutboxDispatcherTest {

    @Mock
    private AuthAuditOutboxMapper outboxMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private StreamOperations<String, Object, Object> streamOperations;

    private AuthAuditOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new AuthAuditOutboxDispatcher(outboxMapper, redisTemplate, new ObjectMapper());
        lenient().when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
    }

    @Test
    void dispatchesClaimedAuditRow() {
        AuthAuditOutboxRecord record = new AuthAuditOutboxRecord();
        record.setId("auth-audit-1");
        when(outboxMapper.selectPending(50)).thenReturn(List.of(record));
        when(outboxMapper.claim(eq("auth-audit-1"), anyString())).thenReturn(1);
        when(streamOperations.add(any(MapRecord.class))).thenReturn(RecordId.of("1-0"));
        when(outboxMapper.markDelivered(eq("auth-audit-1"), anyString())).thenReturn(1);

        assertThat(dispatcher.dispatch()).isEqualTo(1);

        verify(streamOperations).add(argThat(record ->
                IntegrationEventEnvelopeContract.AUTH_AUDIT_STREAM_KEY.equals(record.getStream())));
        verify(outboxMapper).markDelivered(eq("auth-audit-1"), anyString());
    }

    @Test
    void retriesWhenRedisPublishFails() {
        AuthAuditOutboxRecord record = new AuthAuditOutboxRecord();
        record.setId("auth-audit-2");
        when(outboxMapper.selectPending(50)).thenReturn(List.of(record));
        when(outboxMapper.claim(eq("auth-audit-2"), anyString())).thenReturn(1);
        when(streamOperations.add(any(MapRecord.class)))
                .thenThrow(new IllegalStateException("redis down"));

        assertThat(dispatcher.dispatch()).isZero();

        verify(outboxMapper).markRetry(
                eq("auth-audit-2"), anyString(), eq("redis down"), eq(5), eq(30));
        verify(outboxMapper, never()).markDelivered(anyString(), anyString());
    }
}
