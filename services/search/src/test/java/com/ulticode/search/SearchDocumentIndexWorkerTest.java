package com.ulticode.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.ulticode.common.event.SearchDocumentChangedEventContract;
import com.ulticode.search.config.SearchWorkerProperties;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
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
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SearchDocumentIndexWorker")
class SearchDocumentIndexWorkerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private StreamOperations<String, Object, Object> streamOps;
    @Mock private HashOperations<String, Object, Object> hashOps;
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
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        when(meiliSearchClient.index(anyString())).thenReturn(meiliIndex);
    }

    private MapRecord<String, String, String> record(String id, String eventType, String payload) {
        return record(id, eventType, payload, "0");
    }

    private MapRecord<String, String, String> record(String id, String eventType, String payload, String version) {
        return StreamRecords.mapBacked(Map.of(
                        "eventId", "evt-" + id,
                        "owner", "App",
                        "eventType", eventType,
                        "aggregateId", "doc-" + id,
                        "aggregateVersion", version,
                        "schemaVersion", "1",
                        "causationId", "cause-" + id,
                        "traceId", "trace-" + id,
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
                .thenReturn(List.of(record("1", SearchDocumentChangedEventContract.EVENT_TYPE, upsertPayload("problems"), "100")));

        int processed = worker.consume();

        assertThat(processed).isEqualTo(1);
        verify(meiliIndex).addDocuments("{\"id\":\"doc-1\",\"title\":\"T\",\"_aggregateVersion\":100}");
        verify(hashOps).put("search:doc-version:problems", "doc-1", "100");
        verify(streamOps).acknowledge("stream:integration", "search-worker", RecordId.of("evt-1"));
    }

    @Test
    @DisplayName("stale UPSERT (ledger newer) is skipped, not written, and still ACKed")
    void staleSnapshotIsSkippedAndAcked() {
        stubBusyGroup();
        stubEmptyReads();
        when(hashOps.get("search:doc-version:problems", "doc-1")).thenReturn("200");
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record("1", SearchDocumentChangedEventContract.EVENT_TYPE, upsertPayload("problems"), "150")));

        int processed = worker.consume();

        assertThat(processed).isEqualTo(1);
        verify(meiliSearchClient, never()).index(anyString());
        verify(hashOps, never()).put(anyString(), any(), any());
        verify(streamOps).acknowledge("stream:integration", "search-worker", RecordId.of("evt-1"));
    }

    @Test
    @DisplayName("equal-version UPSERT rewrites (idempotent) and keeps the ledger")
    void equalVersionRewrites() {
        stubBusyGroup();
        stubEmptyReads();
        when(hashOps.get("search:doc-version:posts", "doc-1")).thenReturn("100");
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record("1", SearchDocumentChangedEventContract.EVENT_TYPE, upsertPayload("posts"), "100")));

        worker.consume();

        verify(meiliIndex).addDocuments("{\"id\":\"doc-1\",\"title\":\"T\",\"_aggregateVersion\":100}");
        verify(hashOps).put("search:doc-version:posts", "doc-1", "100");
    }

    @Test
    @DisplayName("unparsable or missing ledger entry does not block the write")
    void corruptLedgerEntryIsIgnored() {
        stubBusyGroup();
        stubEmptyReads();
        when(hashOps.get("search:doc-version:problems", "doc-1")).thenReturn("not-a-number");
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record("1", SearchDocumentChangedEventContract.EVENT_TYPE, upsertPayload("problems"), "100")));

        worker.consume();

        verify(meiliIndex).addDocuments("{\"id\":\"doc-1\",\"title\":\"T\",\"_aggregateVersion\":100}");
        verify(hashOps).put("search:doc-version:problems", "doc-1", "100");
    }

    @Test
    @DisplayName("DELETE tombstones the document, records a negative ledger version and ACKs")
    void deleteTombstonesAndAcks() {
        stubBusyGroup();
        stubEmptyReads();
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record("2", SearchDocumentChangedEventContract.EVENT_TYPE, deletePayload("users"), "200")));

        worker.consume();

        verify(meiliIndex).deleteDocument("doc-2");
        verify(hashOps).put("search:doc-version:users", "doc-2", "-200");
        verify(streamOps).acknowledge("stream:integration", "search-worker", RecordId.of("evt-2"));
    }

    @Test
    @DisplayName("UPSERT not newer than a tombstone is skipped (no resurrection)")
    void upsertNotNewerThanTombstoneIsSkipped() {
        stubBusyGroup();
        stubEmptyReads();
        when(hashOps.get("search:doc-version:problems", "doc-1")).thenReturn("-200");
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record("1", SearchDocumentChangedEventContract.EVENT_TYPE,
                        upsertPayload("problems"), "200")));

        worker.consume();

        verify(meiliSearchClient, never()).index(anyString());
        verify(streamOps).acknowledge("stream:integration", "search-worker", RecordId.of("evt-1"));
    }

    @Test
    @DisplayName("UPSERT strictly newer than a tombstone republishes (re-create)")
    void upsertNewerThanTombstoneRepublishes() {
        stubBusyGroup();
        stubEmptyReads();
        when(hashOps.get("search:doc-version:problems", "doc-1")).thenReturn("-200");
        when(streamOps.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
                .thenReturn(List.of(record("1", SearchDocumentChangedEventContract.EVENT_TYPE,
                        upsertPayload("problems"), "250")));

        worker.consume();

        verify(meiliIndex).addDocuments("{\"id\":\"doc-1\",\"title\":\"T\",\"_aggregateVersion\":250}");
        verify(hashOps).put("search:doc-version:problems", "doc-1", "250");
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
        // Ledger must not advance until MeiliSearch accepted the write (DEC-016).
        verify(hashOps, never()).put(anyString(), any(), any());
    }

    @Test
    @DisplayName("exhausted entries use atomic Redis DLQ transfer")
    void exhaustedEntryIsDeadLettered() {
        stubBusyGroup();
        SearchWorkerProperties props = new SearchWorkerProperties();
        props.setMaxAttempts(3);
        worker = new SearchDocumentIndexWorker(redisTemplate, meiliSearchClient, objectMapper, props,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        when(redisTemplate.opsForStream()).thenReturn(streamOps);
        PendingMessage stale = new PendingMessage(
                RecordId.of("evt-9"), Consumer.from("search-worker", "search-worker-1"),
                java.time.Duration.ofSeconds(60), 4L);
        when(streamOps.pending(anyString(), anyString(), any(org.springframework.data.domain.Range.class), anyLong()))
                .thenReturn(new PendingMessages("stream:integration", org.springframework.data.domain.Range.unbounded(),
                        List.of(stale)));
        when(streamOps.range(anyString(), any(org.springframework.data.domain.Range.class)))
                .thenReturn(List.of(record("9", SearchDocumentChangedEventContract.EVENT_TYPE,
                        upsertPayload("problems"))));
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(1L);
        worker.consume();

        ArgumentCaptor<org.springframework.data.redis.core.script.RedisScript> script =
                ArgumentCaptor.forClass(org.springframework.data.redis.core.script.RedisScript.class);
        ArgumentCaptor<List> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(script.capture(), keys.capture(), args.capture());

        assertThat(script.getValue().getScriptAsString())
                .contains("'owner'", "'schemaVersion'", "'causationId'", "'traceId'");
        assertThat(Arrays.asList(args.getValue()))
                .containsExactly(
                        "evt-9", "App", SearchDocumentChangedEventContract.EVENT_TYPE,
                        "doc-9", "0", "1", "cause-9", "trace-9",
                        upsertPayload("problems"), "86400", "search-worker", "evt-9");
    }

}
