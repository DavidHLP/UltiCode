package com.ulticode.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.ulticode.common.event.SearchDocumentChangedEventContract;
import com.ulticode.search.config.SearchWorkerProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SearchDocumentIndexWorker")
class SearchDocumentIndexWorkerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private StreamOperations<String, Object, Object> streamOps;
    @Mock private Client meiliSearchClient;
    @Mock private Index meiliIndex;

    private SearchDocumentIndexWorker worker;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        SearchWorkerProperties props = new SearchWorkerProperties();
        props.setEnabled(true);
        worker = new SearchDocumentIndexWorker(redisTemplate, meiliSearchClient, objectMapper, props,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        when(meiliSearchClient.index(anyString())).thenReturn(meiliIndex);
    }

    private MapRecord<String, String, String> record(String id, String eventType, String payload) {
        return StreamRecords.mapBacked(Map.of(
                        "eventId", "evt-" + id,
                        "eventType", eventType,
                        "aggregateId", "doc-" + id,
                        "payload", payload))
                .withStreamKey("stream:integration")
                .withId(RecordId.of("evt-" + id));
    }

    private String upsertPayload(String index) {
        return "{\"index\":\"" + index + "\",\"operation\":\"UPSERT\","
                + "\"document\":{\"id\":\"doc-1\",\"title\":\"T\"},\"occurredAt\":\"2026-08-16T00:00:00Z\"}";
    }

    private String deletePayload(String index) {
        return "{\"index\":\"" + index + "\",\"operation\":\"DELETE\",\"occurredAt\":\"2026-08-16T00:00:00Z\"}";
    }

    private void stubBusyGroup() {
        // Group already exists on restart: createGroup returns OK.
        when(streamOps.createGroup(anyString(), any(ReadOffset.class), anyString()))
                .thenReturn("OK");
    }

    private void stubEmptyReads() {
        when(streamOps.pending(anyString(), anyString(), any(org.springframework.data.domain.Range.class), anyLong()))
                .thenReturn(new PendingMessages("stream:integration", org.springframework.data.domain.Range.unbounded(), List.of()));
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("non-search events are ACKed without touching MeiliSearch")
    void nonSearchEventIsAckedAndSkipped() {
        stubBusyGroup();
        when(streamOps.pending(anyString(), anyString(), any(org.springframework.data.domain.Range.class), anyLong()))
                .thenReturn(new PendingMessages("stream:integration", org.springframework.data.domain.Range.unbounded(), List.of()));
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record("1", "SubmissionJudged", "{}")));

        int processed = worker.consume();

        assertThat(processed).isEqualTo(1);
        verify(streamOps).acknowledge("stream:integration", "search-worker", RecordId.of("evt-1"));
        verify(meiliSearchClient, never()).index(anyString());
    }

    @Test
    @DisplayName("UPSERT writes the document to the allowlisted MeiliSearch index and ACKs")
    void upsertWritesToMeiliAndAcks() throws Exception {
        stubBusyGroup();
        stubEmptyReads();
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record("1", SearchDocumentChangedEventContract.EVENT_TYPE, upsertPayload("problems"))));

        int processed = worker.consume();

        assertThat(processed).isEqualTo(1);
        verify(meiliIndex).addDocuments("{\"id\":\"doc-1\",\"title\":\"T\"}");
        verify(streamOps).acknowledge("stream:integration", "search-worker", RecordId.of("evt-1"));
    }

    @Test
    @DisplayName("DELETE tombstones the document by aggregate id and ACKs")
    void deleteTombstonesAndAcks() {
        stubBusyGroup();
        stubEmptyReads();
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record("2", SearchDocumentChangedEventContract.EVENT_TYPE, deletePayload("users"))));

        worker.consume();

        verify(meiliIndex).deleteDocument("doc-2");
        verify(streamOps).acknowledge("stream:integration", "search-worker", RecordId.of("evt-2"));
    }

    @Test
    @DisplayName("unsupported index keeps the entry in the PEL (no ACK)")
    void unsupportedIndexIsNotAcked() {
        stubBusyGroup();
        stubEmptyReads();
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record("3", SearchDocumentChangedEventContract.EVENT_TYPE,
                        upsertPayload("secrets"))));

        worker.consume();

        verify(streamOps, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    @DisplayName("MeiliSearch failure keeps the entry in the PEL (no ACK)")
    void meiliFailureIsNotAcked() {
        stubBusyGroup();
        stubEmptyReads();
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record("4", SearchDocumentChangedEventContract.EVENT_TYPE, upsertPayload("posts"))));
        when(meiliIndex.addDocuments(anyString()))
                .thenThrow(new RuntimeException("MeiliSearch unreachable"));

        worker.consume();

        verify(streamOps, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    @DisplayName("entries past max deliveries are dead-lettered and ACKed")
    void exhaustedEntryIsDeadLettered() {
        stubBusyGroup();
        SearchWorkerProperties props = new SearchWorkerProperties();
        props.setMaxAttempts(3);
        worker = new SearchDocumentIndexWorker(redisTemplate, meiliSearchClient, objectMapper, props,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        when(redisTemplate.opsForStream()).thenReturn(streamOps);

        PendingMessage stale = new PendingMessage(
                RecordId.of("evt-9"), Consumer.from("search-worker", "search-worker-1"), java.time.Duration.ofSeconds(60), 4L);
        when(streamOps.pending(anyString(), anyString(), any(org.springframework.data.domain.Range.class), anyLong()))
                .thenReturn(new PendingMessages("stream:integration", org.springframework.data.domain.Range.unbounded(), List.of(stale)));
        when(streamOps.range(anyString(), any(org.springframework.data.domain.Range.class)))
                .thenReturn(List.of(record("9", SearchDocumentChangedEventContract.EVENT_TYPE, upsertPayload("problems"))));
        when(streamOps.add(any(MapRecord.class))).thenReturn(RecordId.of("dlq-1"));

        worker.consume();

        verify(streamOps).add(any(MapRecord.class));
        verify(streamOps).acknowledge("stream:integration", "search-worker", RecordId.of("evt-9"));
        verify(meiliSearchClient, never()).index(anyString());
    }
}
