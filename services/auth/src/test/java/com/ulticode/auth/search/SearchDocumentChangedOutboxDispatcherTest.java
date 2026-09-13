package com.ulticode.auth.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
        record.setAggregateVersion(42L);
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
        when(outboxMapper.claimPending(anyString(), eq(50))).thenReturn(1);
        when(outboxMapper.selectClaimed(anyString())).thenReturn(List.of(pending("e-1")));
        when(streamOps.add(any(MapRecord.class))).thenReturn(RecordId.of("1-0"));
        when(outboxMapper.markDelivered(eq("e-1"), anyString())).thenReturn(1);

        int delivered = dispatcher.dispatch();

        assertThat(delivered).isEqualTo(1);
        verify(outboxMapper).reclaimStaleClaimed(eq(120));
        verify(outboxMapper).claimPending(anyString(), eq(50));
        verify(outboxMapper).selectClaimed(anyString());
        verify(outboxMapper).markDelivered(eq("e-1"), anyString());

        ArgumentCaptor<MapRecord> streamRecord = ArgumentCaptor.forClass(MapRecord.class);
        verify(streamOps).add(streamRecord.capture());
        assertThat(streamRecord.getValue().getStream()).isEqualTo("stream:integration");
        @SuppressWarnings("unchecked")
        Map<String, String> fields = (Map<String, String>) streamRecord.getValue().getValue();
        assertThat(fields)
                .containsEntry("eventId", "e-1")
                .containsEntry("owner", "Auth")
                .containsEntry("aggregateId", "u-1")
                .containsEntry("aggregateVersion", "42")
                .containsEntry("eventType", "SearchDocumentChanged")
                .containsEntry("schemaVersion", "1")
                .containsEntry("payload", "{\"index\":\"users\",\"operation\":\"UPSERT\"}");
    }

    @Test
    @DisplayName("XADD failure marks the row retryable, not delivered")
    void dispatch_xaddFailureMarksRetry() {
        when(outboxMapper.claimPending(anyString(), eq(50))).thenReturn(1);
        when(outboxMapper.selectClaimed(anyString())).thenReturn(List.of(pending("e-2")));
        when(streamOps.add(any(MapRecord.class))).thenThrow(new IllegalStateException("redis down"));

        int delivered = dispatcher.dispatch();

        assertThat(delivered).isZero();
        verify(outboxMapper).markRetry(eq("e-2"), anyString(), eq("redis down"), eq(5), eq(30));
        verify(outboxMapper, never()).markDelivered(any(), any());
    }

    @Test
    @DisplayName("a batch claimed by another replica is skipped")
    void dispatch_skipsWhenNothingIsClaimed() {
        when(outboxMapper.claimPending(anyString(), eq(50))).thenReturn(0);

        int delivered = dispatcher.dispatch();

        assertThat(delivered).isZero();
        verify(outboxMapper, never()).selectClaimed(anyString());
        verify(streamOps, never()).add(any(MapRecord.class));
    }

    @Test
    @DisplayName("lost delivery confirmation does not count the row as delivered")
    void dispatch_doesNotCountLostConfirmation() {
        when(outboxMapper.claimPending(anyString(), eq(50))).thenReturn(1);
        when(outboxMapper.selectClaimed(anyString())).thenReturn(List.of(pending("e-4")));
        when(streamOps.add(any(MapRecord.class))).thenReturn(RecordId.of("2-0"));
        when(outboxMapper.markDelivered(eq("e-4"), anyString())).thenReturn(0);

        assertThat(dispatcher.dispatch()).isZero();

        verify(outboxMapper, never()).markRetry(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("drain rejects a new batch")
    void dispatch_rejectsAfterContextClosed() {
        dispatcher.onContextClosed(null);

        assertThat(dispatcher.dispatch()).isZero();

        verify(outboxMapper, never()).reclaimStaleClaimed(anyInt());
        verify(outboxMapper, never()).claimPending(anyString(), anyInt());
    }
}
