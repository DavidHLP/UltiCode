package com.ulticode.search;

import static org.assertj.core.api.Assertions.assertThat;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ulticode.common.event.SearchDocumentChangedEventContract;
import com.ulticode.search.config.SearchWorkerProperties;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * SEARCH-003 slice-4: end-to-end transport evidence for the indexing worker
 * with a real Redis Stream and a real meilisearch-java HTTP client against a
 * minimal MeiliSearch REST stub (JDK HttpServer, no new dependencies).
 *
 * <p>The real MeiliSearch container image cannot be pulled (Docker Hub is
 * unreachable in this environment — recorded external gap). The stub
 * implements exactly the two write endpoints the worker calls
 * (POST /indexes/{uid}/documents, DELETE /indexes/{uid}/documents/{id}) so
 * the consume → XACK → version-ledger → Meili write chain is exercised over
 * the real transports.
 */
@Testcontainers
@DisplayName("SEARCH-003: worker end-to-end (real Redis + Meili REST stub)")
class SearchWorkerEndToEndIT {

    private static final String STREAM = "stream:integration";
    private static final String GROUP = "search-worker";

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    private static HttpServer meiliStub;
    private static String meiliBaseUrl;
    private static final Map<String, Map<String, Map<String, Object>>> INDEX_DOCS =
            new ConcurrentHashMap<>();
    private static final List<String> WRITE_BODIES = new ArrayList<>();
    private static final List<String> DELETED_IDS = new ArrayList<>();
    private static final AtomicInteger TASK_UID = new AtomicInteger(1);

    private static StringRedisTemplate redis;
    private static Client meiliClient;
    private static SearchDocumentIndexWorker worker;
    private static SearchWorkerProperties props;

    @BeforeAll
    static void setUp() throws Exception {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);

        meiliStub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        meiliStub.createContext("/", SearchWorkerEndToEndIT::handleMeili);
        meiliStub.start();
        meiliBaseUrl = "http://127.0.0.1:" + meiliStub.getAddress().getPort();

        meiliClient = new Client(new Config(meiliBaseUrl, ""));

        props = new SearchWorkerProperties();
        props.setEnabled(true);
        props.setConsumerName("search-worker-it");
        worker = new SearchDocumentIndexWorker(redis, meiliClient,
                new com.fasterxml.jackson.databind.ObjectMapper(), props,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    @AfterAll
    static void tearDown() {
        meiliStub.stop(0);
    }

    @BeforeEach
    void resetState() {
        INDEX_DOCS.clear();
        WRITE_BODIES.clear();
        DELETED_IDS.clear();
        redis.delete(List.of(STREAM, props.getDlqKey(), props.getVersionKeyPrefix() + ":problems"));
    }

    private static void handleMeili(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if ("POST".equals(method) && path.matches("/indexes/[^/]+/documents")) {
                String uid = path.split("/")[2];
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                WRITE_BODIES.add(body);
                @SuppressWarnings("unchecked")
                Map<String, Object> doc = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(body, Map.class);
                INDEX_DOCS.computeIfAbsent(uid, k -> new ConcurrentHashMap<>())
                        .put(String.valueOf(doc.get("id")), doc);
                respond(exchange, 202, taskJson(uid, "documentAdditionOrUpdate"));
            } else if ("DELETE".equals(method) && path.matches("/indexes/[^/]+/documents/[^/]+")) {
                String[] parts = path.split("/");
                String uid = parts[2];
                String id = parts[4];
                DELETED_IDS.add(id);
                Map<String, Map<String, Object>> docs = INDEX_DOCS.get(uid);
                if (docs != null) {
                    docs.remove(id);
                }
                respond(exchange, 202, taskJson(uid, "documentDeletion"));
            } else {
                respond(exchange, 404, "{\"message\":\"stub: no route " + path + "\"}");
            }
        } catch (Exception e) {
            respond(exchange, 500, "{\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    private static String taskJson(String uid, String type) {
        return "{\"taskUid\":" + TASK_UID.getAndIncrement()
                + ",\"indexUid\":\"" + uid + "\",\"status\":\"enqueued\",\"type\":\"" + type + "\"}";
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private RecordId xadd(String id, String version, String payload) {
        return xadd(id, version, payload, Map.of());
    }

    private RecordId xadd(String id, String version, String payload, Map<String, String> extraFields) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventId", "evt-" + id);
        fields.put("owner", "App");
        fields.put("eventType", SearchDocumentChangedEventContract.EVENT_TYPE);
        fields.put("aggregateId", "doc-" + id);
        fields.put("aggregateVersion", version);
        fields.put("schemaVersion", "1");
        fields.putAll(extraFields);
        fields.put("payload", payload);
        MapRecord<String, String, String> record = StreamRecords.mapBacked(fields)
                .withStreamKey(STREAM)
                .withId(RecordId.autoGenerate());
        return redis.opsForStream().add(record);
    }

    private String upsertPayload() {
        return "{\"index\":\"problems\",\"operation\":\"UPSERT\","
                + "\"document\":{\"id\":\"doc-1\",\"title\":\"Two Sum\"},\"occurredAt\":\"2026-08-16T10:00:00\"}";
    }

    private String deletePayload() {
        return "{\"index\":\"problems\",\"operation\":\"DELETE\",\"occurredAt\":\"2026-08-16T10:01:00\"}";
    }

    private long pendingCount() {
        return redis.opsForStream().pending(STREAM, GROUP).getTotalPendingMessages();
    }

    @Test
    @DisplayName("UPSERT flows real Redis -> real Meili HTTP client -> XACK + ledger")
    void upsertEndToEnd() throws Exception {
        xadd("1", "100", upsertPayload());

        int processed = worker.consume();

        assertThat(processed).isEqualTo(1);
        assertThat(pendingCount()).isZero();
        assertThat(WRITE_BODIES).hasSize(1);
        assertThat(WRITE_BODIES.get(0))
                .contains("\"id\":\"doc-1\"")
                .contains("\"title\":\"Two Sum\"")
                .contains("\"_aggregateVersion\":100");
        assertThat(redis.<String, String>opsForHash()
                .get(props.getVersionKeyPrefix() + ":problems", "doc-1")).isEqualTo("100");
    }

    @Test
    @DisplayName("DELETE flows through the stub and records a tombstone version")
    void deleteEndToEnd() {
        xadd("1", "100", upsertPayload());
        worker.consume();
        xadd("2", "200", deletePayload());

        int processed = worker.consume();

        assertThat(processed).isEqualTo(1);
        assertThat(DELETED_IDS).containsExactly("doc-2");
        assertThat(redis.<String, String>opsForHash()
                .get(props.getVersionKeyPrefix() + ":problems", "doc-2")).isEqualTo("-200");
        assertThat(pendingCount()).isZero();
    }

    @Test
    @DisplayName("stale UPSERT is skipped at the ledger and still ACKed")
    void staleUpsertEndToEnd() {
        xadd("1", "100", upsertPayload());
        worker.consume();
        // backfill-style older snapshot for the same document id (doc-1)
        xadd("1", "50", upsertPayload());

        int processed = worker.consume();

        assertThat(processed).isEqualTo(1);
        assertThat(WRITE_BODIES).hasSize(1); // no second Meili write
        assertThat(pendingCount()).isZero();
    }

    @Test
    @DisplayName("exhausted events preserve the full envelope in the real Redis DLQ")
    void exhaustedEventPreservesEnvelope() {
        int previousMaxAttempts = props.getMaxAttempts();
        props.setMaxAttempts(0);
        try {
            xadd("dlq-1", "100", "{\"index\":\"unsupported\",\"operation\":\"UPSERT\"}",
                    Map.of("causationId", "cause-1", "traceId", "trace-1"));

            // First delivery fails and remains in the PEL; the next cycle dead-letters it.
            worker.consume();
            worker.consume();

            var dlq = redis.opsForStream()
                    .range(props.getDlqKey(), org.springframework.data.domain.Range.unbounded());
            assertThat(dlq).hasSize(1);
            assertThat(dlq.get(0).getValue())
                    .containsEntry("eventId", "evt-dlq-1")
                    .containsEntry("owner", "App")
                    .containsEntry("eventType", SearchDocumentChangedEventContract.EVENT_TYPE)
                    .containsEntry("aggregateId", "doc-dlq-1")
                    .containsEntry("aggregateVersion", "100")
                    .containsEntry("schemaVersion", "1")
                    .containsEntry("causationId", "cause-1")
                    .containsEntry("traceId", "trace-1");
            assertThat(pendingCount()).isZero();
        } finally {
            props.setMaxAttempts(previousMaxAttempts);
        }
    }
}
