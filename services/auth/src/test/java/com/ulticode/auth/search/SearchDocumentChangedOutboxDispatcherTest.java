package com.ulticode.auth.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisStreamCommands;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchDocumentChangedOutboxDispatcher")
class SearchDocumentChangedOutboxDispatcherTest {

    @Mock private SearchDocumentChangedOutboxMapper outboxMapper;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private StreamOperations<String, Object, Object> streamOps;

    private SearchDocumentChangedOutboxDispatcher dispatcher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        dispatcher = new SearchDocumentChangedOutboxDispatcher(
                outboxMapper, redisTemplate, new ObjectMapper());
        lenient().when(redisTemplate.opsForStream()).thenReturn(streamOps);
    }

    private SearchDocumentChangedOutboxRecord pending(String id) {
        SearchDocumentChangedOutboxRecord record = new SearchDocumentChangedOutboxRecord();
        record.setId(id);
        record.setOwner("Auth");
        record.setAggregateId("u-1");
        record.setEventType("SearchDocumentChanged");
        record.setSchemaVersion(1);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("index", "users");
        payload.put("operation", "UPSERT");
        record.setPayload(payload);
        return record;
    }

    @Test
    @DisplayName("claims, XADDs and marks delivered for each pending row")
    void dispatch_deliversPendingRows() {
        when(outboxMapper.selectPending(50)).thenReturn(List.of(pending("e-1")));
        when(outboxMapper.claim(eq("e-1"), any())).thenReturn(1);
        when(streamOps.add(any(MapRecord.class))).thenReturn(RecordId.of("1-0"));

        int delivered = dispatcher.dispatch();

        assertThat(delivered).isEqualTo(1);
        verify(outboxMapper).markDelivered(eq("e-1"), any());
        verify(streamOps).add(any(MapRecord.class));
    }

    @Test
    @DisplayName("XADD failure marks the row retryable, not delivered")
    void dispatch_xaddFailureMarksRetry() {
        when(outboxMapper.selectPending(50)).thenReturn(List.of(pending("e-2")));
        when(outboxMapper.claim(eq("e-2"), any())).thenReturn(1);
        when(streamOps.add(any(MapRecord.class))).thenThrow(new IllegalStateException("redis down"));

        int delivered = dispatcher.dispatch();

        assertThat(delivered).isZero();
        verify(outboxMapper).markRetry(eq("e-2"), any(), any(), eq(5), eq(30));
        verify(outboxMapper, org.mockito.Mockito.never()).markDelivered(any(), any());
    }

    @Test
    @DisplayName("rows already claimed by another replica are skipped")
    void dispatch_skipsAlreadyClaimed() {
        when(outboxMapper.selectPending(50)).thenReturn(List.of(pending("e-3")));
        when(outboxMapper.claim(eq("e-3"), any())).thenReturn(0);

        int delivered = dispatcher.dispatch();

        assertThat(delivered).isZero();
        verify(streamOps, org.mockito.Mockito.never()).add(any(MapRecord.class));
    }
}
