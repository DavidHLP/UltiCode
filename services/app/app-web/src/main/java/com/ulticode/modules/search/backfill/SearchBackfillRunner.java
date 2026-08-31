package com.ulticode.modules.search.backfill;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.exceptions.MeilisearchException;
import com.meilisearch.sdk.model.DocumentsQuery;
import com.meilisearch.sdk.model.Results;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.common.resilience.DependencyGuard;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * SEARCH-003 backfill runner (DEC-017).
 *
 * <p>Converges a MeiliSearch index to the current owner-database state
 * without ever writing MeiliSearch directly: every change is published as a
 * {@code SearchDocumentChanged} event through the integration outbox and the
 * Search worker stays the sole index writer.
 *
 * <p>Protocol per index:
 * <ol>
 *   <li>watermark {@code W} = now (epoch millis);</li>
 *   <li>enumerate the full snapshot page by page (stable natural-key order);</li>
 *   <li>preflight-read the existing index documents ({@code id} +
 *       {@code _aggregateVersion}); Meili unreachable fails the run before
 *       anything is published;</li>
 *   <li>publish UPSERT for every snapshot document with its row version;</li>
 *   <li>publish DELETE for existing documents whose version is strictly older
 *       than {@code W} and absent from the snapshot — documents created or
 *       updated during the run (version &ge; W) are left to their live
 *       events, so a concurrent write is never deleted by the diff.</li>
 * </ol>
 *
 * <p>Live-event races are resolved at the worker version ledger (DEC-016):
 * a snapshot UPSERT whose version is older than an already-indexed live
 * write is skipped. Re-running the runner converges idempotently.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = {"app.search.backfill.enabled", "meilisearch.enabled"},
        havingValue = "true")
public class SearchBackfillRunner implements ApplicationRunner {

    private static final String VERSION_FIELD = "_aggregateVersion";

    private final List<SearchBackfillReadPort> backfillPorts;
    private final SearchDocumentChangedPublisher publisher;
    private final Client meiliSearchClient;
    private final Clock clock;
    private final SearchBackfillProperties props;
    private final DependencyGuard meiliSearchGuard =
            new DependencyGuard(1, 5, Duration.ofSeconds(30));

    @Override
    public void run(ApplicationArguments args) {
        Map<SearchIndexType, SearchBackfillReadPort> byType = new HashMap<>();
        for (SearchBackfillReadPort port : backfillPorts) {
            byType.put(port.type(), port);
        }
        for (SearchIndexType type : selectedIndexes()) {
            SearchBackfillReadPort port = byType.get(type);
            if (port == null) {
                throw new IllegalStateException("no backfill port for index " + type);
            }
            runIndex(type, port);
        }
    }

    private List<SearchIndexType> selectedIndexes() {
        String raw = props.getIndexes();
        if (raw == null || raw.isBlank()) {
            return List.of(SearchIndexType.values());
        }
        List<SearchIndexType> selected = new ArrayList<>();
        for (String part : raw.split(",")) {
            selected.add(SearchIndexType.valueOf(part.trim().toUpperCase(Locale.ROOT)));
        }
        return selected;
    }

    private void runIndex(SearchIndexType type, SearchBackfillReadPort port) {
        long watermark = clock.instant().toEpochMilli();

        List<SearchBackfillDocument> snapshot = new ArrayList<>();
        int offset = 0;
        List<SearchBackfillDocument> page;
        do {
            page = port.enumerateForBackfill(offset, props.getPageSize());
            snapshot.addAll(page);
            offset += page.size();
        } while (!page.isEmpty());

        Map<String, Long> existing = readExistingVersions(type.getIndexName());
        Set<String> snapshotIds = snapshot.stream()
                .map(SearchBackfillDocument::documentId)
                .collect(Collectors.toSet());
        List<SearchBackfillDocument> deletes = existing.entrySet().stream()
                .filter(e -> e.getValue() < watermark && !snapshotIds.contains(e.getKey()))
                .map(e -> new SearchBackfillDocument(e.getKey(), e.getValue(), null))
                .toList();

        for (SearchBackfillDocument doc : snapshot) {
            publisher.publishBackfill(
                    type.getIndexName(), doc.documentId(), doc.versionMillis(), doc.document());
        }
        for (SearchBackfillDocument tombstone : deletes) {
            publisher.publishBackfill(
                    type.getIndexName(), tombstone.documentId(), tombstone.versionMillis(), null);
        }

        log.info("backfill {} complete: snapshot={} existing={} upserts={} deletes={} watermark={}",
                type.getIndexName(), snapshot.size(), existing.size(),
                snapshot.size(), deletes.size(), watermark);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> readExistingVersions(String indexName) {
        DependencyGuard.Permit permit = meiliSearchGuard.acquire();
        try (permit) {
            try {
                Map<String, Long> versions = new HashMap<>();
                int offset = 0;
                Map[] results;
                do {
                    Results<Map> page = meiliSearchClient.index(indexName).getDocuments(
                            new DocumentsQuery()
                                    .setLimit(props.getPageSize())
                                    .setOffset(offset)
                                    .setFields(new String[]{"id", VERSION_FIELD}),
                            Map.class);
                    results = page.getResults();
                    for (Map<String, Object> doc : results) {
                        Object id = doc.get("id");
                        if (id != null) {
                            versions.put(String.valueOf(id), docVersion(doc));
                        }
                    }
                    offset += results.length;
                } while (results != null && results.length > 0);
                permit.success();
                return versions;
            } catch (MeilisearchException unavailable) {
                permit.failure();
                throw unavailable;
            } catch (RuntimeException failure) {
                permit.ignore();
                throw failure;
            }
        }
    }

    private long docVersion(Map<String, Object> doc) {
        Object version = doc.get(VERSION_FIELD);
        if (version == null) {
            return 0L;
        }
        try {
            return ((Number) version).longValue();
        } catch (ClassCastException e) {
            return Long.parseLong(String.valueOf(version));
        }
    }
}
