package com.ulticode.modules.search.projection;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.ulticode.common.event.SearchDocumentChangedEventContract;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;
import com.ulticode.modules.search.config.SearchReadProperties;
import com.ulticode.modules.search.source.SearchSource;
import com.ulticode.search.SearchDocumentIndexWorker;
import com.ulticode.search.config.SearchWorkerProperties;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Disposable proof of the existing Search event -> worker -> read projection
 * seam using real Redis and MeiliSearch transports.
 */
@Testcontainers
@DisplayName("SEARCH-003: disposable event-to-query seam")
class SearchEventToQueryE2EIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "");
    private static final AtomicInteger CASE = new AtomicInteger();

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Container
    private static final GenericContainer<?> MEILI = new GenericContainer<>("getmeili/meilisearch:v1.8")
            .withEnv("MEILI_ENV", "development")
            .withEnv("MEILI_NO_ANALYTICS", "true")
            .withExposedPorts(7700)
            .waitingFor(Wait.forHttp("/health").forPort(7700).forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)));

    private static LettuceConnectionFactory redisFactory;
    private static StringRedisTemplate redis;
    private static Client meili;
    private SearchWorkerProperties props;
    private SearchDocumentIndexWorker worker;
    private DefaultSearchReadProjection projection;
    private StubSearchSource problemSource;
    private String stream;

    @BeforeAll
    static void startClients() {
        redisFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379)));
        redisFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(redisFactory);
        meili = new Client(new Config(
                "http://" + MEILI.getHost() + ":" + MEILI.getMappedPort(7700), ""));
    }

    @AfterAll
    static void stopClients() {
        if (redisFactory != null) {
            redisFactory.destroy();
        }
    }

    @BeforeEach
    void setUpCase() {
        String suffix = RUN_ID + "-" + CASE.incrementAndGet();
        stream = "stream:search-e2e:" + suffix;
        props = new SearchWorkerProperties();
        props.setEnabled(true);
        props.setStreamKey(stream);
        props.setGroup("search-worker-e2e-" + suffix);
        props.setConsumerName("search-worker-e2e-consumer");
        props.setDlqKey("search:stream:dlq:" + suffix);
        props.setVersionKeyPrefix("search:doc-version:" + suffix);
        worker = new SearchDocumentIndexWorker(
                redis, meili, JSON, props,
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());

        problemSource = new StubSearchSource();
        projection = new DefaultSearchReadProjection(List.of(problemSource));
        projection.setMeiliSearchClient(meili);
        projection.setReadMode(SearchReadProperties.Mode.INDEXED);
        projection.setWorkerEnabled(true);
        projection.setFallbackToDatabase(false);
    }

    @Test
    @DisplayName("real event transport indexes, queries, replays, deletes, and blocks stale resurrection")
    void eventToQueryAndVersionedReplay() throws Exception {
        String documentId = "problem-" + RUN_ID;
        String queryText = "search-e2e-" + RUN_ID;
        String payload = payload(SearchDocumentChangedEventContract.UPSERT, documentId, queryText);

        addEvent(documentId, "100", payload);
        assertThat(worker.consume()).isEqualTo(1);

        SearchResponseVO indexed = awaitProjection(queryText, true);
        assertThat(indexed.getTotal()).isEqualTo(1);
        assertThat(indexed.getResults()).singleElement()
                .extracting(SearchResponseVO.SearchResultItem::getId)
                .isEqualTo(documentId);

        // A duplicate envelope is safe: one document id remains in Meili.
        addEvent(documentId, "100", payload);
        assertThat(worker.consume()).isEqualTo(1);
        assertThat(awaitProjection(queryText, true).getResults()).hasSize(1);

        addEvent(documentId, "200",
                payload(SearchDocumentChangedEventContract.DELETE, documentId, queryText));
        assertThat(worker.consume()).isEqualTo(1);
        assertThat(awaitProjection(queryText, false).getTotal()).isZero();

        // A backfill snapshot older than the tombstone cannot resurrect it.
        addEvent(documentId, "150", payload);
        assertThat(worker.consume()).isEqualTo(1);
        assertThat(awaitProjection(queryText, false).getTotal()).isZero();
    }

    @Test
    @DisplayName("retry exhaustion moves the full envelope to the real Redis DLQ")
    void exhaustedEventPreservesEnvelope() {
        props.setMaxAttempts(0);
        String documentId = "dlq-" + RUN_ID;
        addEvent(documentId, "100", "{\"index\":\"unsupported\",\"operation\":\"UPSERT\"}");

        assertThat(worker.consume()).isZero();
        assertThat(worker.consume()).isZero();

        var deadLetters = redis.opsForStream()
                .range(props.getDlqKey(), org.springframework.data.domain.Range.unbounded());
        assertThat(deadLetters).singleElement().satisfies(record -> {
            assertThat(record.getValue())
                    .containsEntry("eventId", "evt-" + documentId)
                    .containsEntry("owner", SearchDocumentChangedEventContract.APP_PUBLISHER)
                    .containsEntry("eventType", SearchDocumentChangedEventContract.EVENT_TYPE)
                    .containsEntry("aggregateId", documentId)
                    .containsEntry("aggregateVersion", "100")
                    .containsEntry("schemaVersion", "1")
                    .containsEntry("causationId", "cause-" + documentId)
                    .containsEntry("traceId", "trace-" + documentId);
        });
        assertThat(redis.opsForStream().pending(stream, props.getGroup()).getTotalPendingMessages())
                .isZero();
    }

    @Test
    @DisplayName("projection falls back to the owner source when Meili is unavailable")
    void dbFallbackRemainsAvailable() {
        String queryText = "fallback-" + RUN_ID;
        problemSource.databaseRows = List.of(SearchResponseVO.SearchResultItem.builder()
                .id("db-fallback")
                .type("PROBLEMS")
                .title("Database fallback")
                .url("/problems/db-fallback")
                .build());
        problemSource.databaseCount = 1;
        projection.setMeiliSearchClient(new Client(new Config("http://127.0.0.1:1", "")));
        projection.setFallbackToDatabase(true);

        SearchResponseVO response = projection.search(query(queryText));

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getResults()).singleElement()
                .extracting(SearchResponseVO.SearchResultItem::getId)
                .isEqualTo("db-fallback");
    }

    private void addEvent(String aggregateId, String version, String payload) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("eventId", "evt-" + aggregateId);
        fields.put("owner", SearchDocumentChangedEventContract.APP_PUBLISHER);
        fields.put("eventType", SearchDocumentChangedEventContract.EVENT_TYPE);
        fields.put("aggregateId", aggregateId);
        fields.put("aggregateVersion", version);
        fields.put("schemaVersion", "1");
        fields.put("causationId", "cause-" + aggregateId);
        fields.put("traceId", "trace-" + aggregateId);
        fields.put("payload", payload);
        redis.opsForStream().add(StreamRecords.mapBacked(fields).withStreamKey(stream));
    }

    private String payload(String operation, String documentId, String queryText) {
        try {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("index", SearchDocumentChangedEventContract.PROBLEMS_INDEX);
            value.put("operation", operation);
            if (SearchDocumentChangedEventContract.UPSERT.equals(operation)) {
                value.put("document", Map.of(
                        "id", documentId,
                        "title", queryText + " problem",
                        "slug", documentId));
            }
            value.put("occurredAt", "2026-08-20T12:00:00Z");
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private SearchQueryDTO query(String queryText) {
        SearchQueryDTO query = new SearchQueryDTO();
        query.setQuery(queryText);
        query.setIndex(SearchIndexType.PROBLEMS);
        query.setPage(1);
        query.setLimit(20);
        return query;
    }

    private SearchResponseVO awaitProjection(String queryText, boolean expectedHit) throws Exception {
        SearchResponseVO response = null;
        for (int attempt = 0; attempt < 60; attempt++) {
            try {
                response = projection.search(query(queryText));
                if (expectedHit == (response.getTotal() > 0)) {
                    return response;
                }
            } catch (RuntimeException ignored) {
                // Meili task application is asynchronous; poll until settled.
            }
            Thread.sleep(100);
        }
        return response == null ? projection.search(query(queryText)) : response;
    }

    private static final class StubSearchSource implements SearchSource {
        private long databaseCount;
        private List<SearchResponseVO.SearchResultItem> databaseRows = List.of();

        @Override
        public SearchIndexType getIndexType() {
            return SearchIndexType.PROBLEMS;
        }

        @Override
        public List<SearchResponseVO.SearchResultItem> searchDatabase(String query, int offset, int limit) {
            return databaseRows;
        }

        @Override
        public long countDatabase(String query) {
            return databaseCount;
        }

        @Override
        public String buildUrl(String entityId) {
            return "/problems/" + entityId;
        }
    }
}
