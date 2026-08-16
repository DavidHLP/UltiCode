package com.ulticode.modules.search.backfill;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.Results;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.source.SearchDocumentChangedPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SearchBackfillRunner")
class SearchBackfillRunnerTest {

    /** Fixed watermark: all test row versions (small numbers) are strictly older. */
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneId.of("UTC"));
    private static final long WATERMARK = 1_786_874_400_000L;

    @Mock private SearchBackfillReadPort problemsPort;
    @Mock private SearchBackfillReadPort postsPort;
    @Mock private SearchBackfillReadPort usersPort;
    @Mock private SearchBackfillReadPort solutionsPort;
    @Mock private SearchDocumentChangedPublisher publisher;
    @Mock private Client meiliSearchClient;
    @Mock private Index meiliIndex;

    private SearchBackfillProperties props;

    @BeforeEach
    void setUp() {
        props = new SearchBackfillProperties();
        props.setPageSize(2);
        when(problemsPort.type()).thenReturn(SearchIndexType.PROBLEMS);
        when(postsPort.type()).thenReturn(SearchIndexType.POSTS);
        when(usersPort.type()).thenReturn(SearchIndexType.USERS);
        when(solutionsPort.type()).thenReturn(SearchIndexType.SOLUTIONS);
        when(meiliSearchClient.index(anyString())).thenReturn(meiliIndex);
    }

    private SearchBackfillRunner runner() {
        return new SearchBackfillRunner(
                List.of(postsPort, problemsPort, usersPort, solutionsPort),
                publisher, meiliSearchClient, FIXED_CLOCK, props);
    }

    @SafeVarargs
    private final Results<Map> resultsOf(Map<String, Object>... docs) {
        Results<Map> results = mock(Results.class);
        when(results.getResults()).thenReturn(docs);
        return results;
    }

    private final Results<Map> emptyResults() {
        return resultsOf();
    }

    private SearchBackfillDocument doc(String id, long version) {
        return new SearchBackfillDocument(id, version, Map.of("id", id));
    }

    @Test
    @DisplayName("publishes UPSERTs for the snapshot and DELETEs only stale absent docs")
    void fullConvergencePublishesUpsertsAndDiffDeletes() {
        when(problemsPort.enumerateForBackfill(0, 2)).thenReturn(List.of(doc("a", 100L), doc("d", 300L)));
        when(problemsPort.enumerateForBackfill(2, 2)).thenReturn(List.of());
        // existing index: a (in snapshot, no delete), b (stale, absent -> delete),
        // c (version >= watermark -> keep), e (no version field -> 0 < W -> delete)
        Results<Map> existing = resultsOf(
                Map.of("id", "a", "_aggregateVersion", 100),
                Map.of("id", "b", "_aggregateVersion", 50),
                Map.of("id", "c", "_aggregateVersion", WATERMARK + 10),
                Map.of("id", "e"));
        Results<Map> empty = emptyResults();
        when(meiliIndex.getDocuments(any(), eq(Map.class))).thenReturn(existing, empty);

        runner().run(null);

        verify(publisher).publishBackfill("problems", "a", 100L, Map.of("id", "a"));
        verify(publisher).publishBackfill("problems", "d", 300L, Map.of("id", "d"));
        verify(publisher).publishBackfill(eq("problems"), eq("b"), eq(50L), eq(null));
        verify(publisher).publishBackfill(eq("problems"), eq("e"), eq(0L), eq(null));
        verify(publisher, never()).publishBackfill(eq("problems"), eq("c"), any(Long.class), any());
    }

    @Test
    @DisplayName("empty index only publishes snapshot UPSERTs")
    void emptyIndexOnlyPublishesUpserts() {
        when(problemsPort.enumerateForBackfill(0, 2)).thenReturn(List.of(doc("a", 100L)));
        when(problemsPort.enumerateForBackfill(2, 2)).thenReturn(List.of());
        Results<Map> empty = resultsOf();
        when(meiliIndex.getDocuments(any(), eq(Map.class))).thenReturn(empty);

        runner().run(null);

        verify(publisher).publishBackfill("problems", "a", 100L, Map.of("id", "a"));
        verify(publisher, never()).publishBackfill(anyString(), anyString(), any(Long.class), eq(null));
    }

    @Test
    @DisplayName("legacy documents without _aggregateVersion are treated as version 0 and deleted")
    void legacyDocsWithoutVersionAreDeleted() {
        when(problemsPort.enumerateForBackfill(0, 2)).thenReturn(List.of());
        Results<Map> existing = resultsOf(Map.of("id", "ghost"));
        Results<Map> empty = emptyResults();
        when(meiliIndex.getDocuments(any(), eq(Map.class))).thenReturn(existing, empty);

        runner().run(null);

        verify(publisher).publishBackfill(eq("problems"), eq("ghost"), eq(0L), eq(null));
    }

    @Test
    @DisplayName("MeiliSearch unavailable fails the run before anything is published")
    void meiliUnavailableFailsBeforePublishing() {
        when(problemsPort.enumerateForBackfill(0, 2)).thenReturn(List.of(doc("a", 100L)));
        when(problemsPort.enumerateForBackfill(2, 2)).thenReturn(List.of());
        when(meiliIndex.getDocuments(any(), eq(Map.class)))
                .thenThrow(new RuntimeException("MeiliSearch unreachable"));

        assertThatThrownBy(() -> runner().run(null))
                .hasMessageContaining("MeiliSearch unreachable");
        verify(publisher, never()).publishBackfill(anyString(), anyString(), any(Long.class), any());
    }

    @Test
    @DisplayName("index selection restricts the run to the configured indexes")
    void indexSelectionRestrictsRun() {
        props.setIndexes("problems");
        when(problemsPort.enumerateForBackfill(0, 2)).thenReturn(List.of());
        Results<Map> empty = resultsOf();
        when(meiliIndex.getDocuments(any(), eq(Map.class))).thenReturn(empty);

        runner().run(null);

        verify(problemsPort).enumerateForBackfill(anyInt(), anyInt());
        verify(postsPort, never()).enumerateForBackfill(anyInt(), anyInt());
        verify(usersPort, never()).enumerateForBackfill(anyInt(), anyInt());
        verify(solutionsPort, never()).enumerateForBackfill(anyInt(), anyInt());
    }

    @Test
    @DisplayName("re-run on a converged index is idempotent: upserts only, no deletes")
    void rerunIsIdempotent() {
        when(problemsPort.enumerateForBackfill(0, 2)).thenReturn(List.of(doc("a", 100L)));
        when(problemsPort.enumerateForBackfill(2, 2)).thenReturn(List.of());
        // index already matches the snapshot exactly
        Results<Map> existing = resultsOf(Map.of("id", "a", "_aggregateVersion", 100));
        Results<Map> empty = emptyResults();
        when(meiliIndex.getDocuments(any(), eq(Map.class))).thenReturn(existing, empty);

        runner().run(null);

        verify(publisher).publishBackfill("problems", "a", 100L, Map.of("id", "a"));
        verify(publisher, never()).publishBackfill(anyString(), anyString(), any(Long.class), eq(null));
    }
}
