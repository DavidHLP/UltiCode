package com.ulticode.modules.search.projection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Config;
import com.meilisearch.sdk.model.TaskInfo;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;
import com.ulticode.modules.search.source.SearchSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Real MeiliSearch gate for SEARCH-003/ARCHFIX-004-004.
 *
 * <p>Run explicitly with a disposable service:
 * MEILI_E2E_HOST=http://127.0.0.1:17700 MEILI_E2E_KEY=... mvnw -Dtest=... test
 */
class DefaultSearchReadProjectionRealMeiliIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "");
    private static final String QUERY = "archfix-real-meili-" + RUN_ID;
    private static Client client;

    @BeforeAll
    static void seedIndexes() throws Exception {
        String host = requireEnv("MEILI_E2E_HOST");
        String key = requireEnv("MEILI_E2E_KEY");
        client = new Client(new Config(host, key));
        Map<String, List<Map<String, Object>>> documents = Map.of(
                "problems", List.of(
                        Map.of("id", id("p1"), "title", QUERY + " problem one", "slug", id("p1")),
                        Map.of("id", id("p2"), "title", QUERY + " problem two", "slug", id("p2")),
                        Map.of("id", id("p3"), "title", QUERY + " problem three", "slug", id("p3"))),
                "users", List.of(
                        Map.of("id", id("u1"), "username", QUERY + " user one"),
                        Map.of("id", id("u2"), "username", QUERY + " user two")),
                "posts", List.of(
                        Map.of("id", id("f1"), "title", QUERY + " forum one")),
                "solutions", List.of(
                        Map.of("id", id("s1"), "title", QUERY + " solution one"),
                        Map.of("id", id("s2"), "title", QUERY + " solution two"),
                        Map.of("id", id("s3"), "title", QUERY + " solution three"),
                        Map.of("id", id("s4"), "title", QUERY + " solution four")));
        for (Map.Entry<String, List<Map<String, Object>>> entry : documents.entrySet()) {
            TaskInfo task = client.index(entry.getKey()).addDocuments(JSON.writeValueAsString(entry.getValue()));
            client.waitForTask(task.getTaskUid());
        }
    }

    @AfterAll
    static void cleanupIndexes() throws Exception {
        if (client == null) {
            return;
        }
        Map<String, List<String>> ids = Map.of(
                "problems", List.of(id("p1"), id("p2"), id("p3")),
                "users", List.of(id("u1"), id("u2")),
                "posts", List.of(id("f1")),
                "solutions", List.of(id("s1"), id("s2"), id("s3"), id("s4")));
        for (Map.Entry<String, List<String>> entry : ids.entrySet()) {
            TaskInfo task = client.index(entry.getKey()).deleteDocuments(entry.getValue());
            client.waitForTask(task.getTaskUid());
        }
    }

    @Test
    void realMeiliSupportsSpecificAndAllIndexTotalsAndOffsets() {
        DefaultSearchReadProjection projection = projection(client);

        SearchQueryDTO specific = query(SearchIndexType.PROBLEMS, 1, 1);
        SearchResponseVO specificResponse = projection.search(specific);
        assertThat(specificResponse.getTotal()).isEqualTo(3);
        assertThat(specificResponse.getResults()).hasSize(1);

        SearchQueryDTO all = query(null, 2, 2);
        SearchResponseVO allResponse = projection.search(all);
        assertThat(allResponse.getTotal()).isEqualTo(10);
        assertThat(allResponse.getResults()).hasSize(2);
    }

    @Test
    void realClientFailureFallsBackForTheWholeRequest() {
        SearchSource problem = source(SearchIndexType.PROBLEMS, "/problems/");
        when(problem.countDatabase(anyString())).thenReturn(1L);
        when(problem.searchDatabase(anyString(), anyInt(), anyInt())).thenReturn(List.of(
                SearchResponseVO.SearchResultItem.builder()
                        .id("db-p1").type("PROBLEMS").title("DB fallback").url("/problems/db-p1").build()));
        DefaultSearchReadProjection projection = new DefaultSearchReadProjection(List.of(
                problem,
                source(SearchIndexType.USERS, "/u/"),
                source(SearchIndexType.POSTS, "/forum/detailed/"),
                source(SearchIndexType.SOLUTIONS, "/solutions/")));
        projection.setMeiliSearchClient(new Client(new Config("http://127.0.0.1:1", "")));

        SearchResponseVO response = projection.search(query(SearchIndexType.PROBLEMS, 1, 1));

        assertThat(response.getTotal()).isEqualTo(1);
        assertThat(response.getResults()).singleElement()
                .extracting(SearchResponseVO.SearchResultItem::getId)
                .isEqualTo("db-p1");
    }

    private static DefaultSearchReadProjection projection(Client searchClient) {
        DefaultSearchReadProjection projection = new DefaultSearchReadProjection(List.of(
                source(SearchIndexType.PROBLEMS, "/problems/"),
                source(SearchIndexType.USERS, "/u/"),
                source(SearchIndexType.POSTS, "/forum/detailed/"),
                source(SearchIndexType.SOLUTIONS, "/solutions/")));
        projection.setMeiliSearchClient(searchClient);
        return projection;
    }

    private static SearchSource source(SearchIndexType type, String prefix) {
        SearchSource source = mock(SearchSource.class);
        when(source.getIndexType()).thenReturn(type);
        when(source.buildUrl(anyString())).thenAnswer(invocation -> prefix + invocation.getArgument(0));
        return source;
    }

    private static SearchQueryDTO query(SearchIndexType type, int page, int limit) {
        SearchQueryDTO query = new SearchQueryDTO();
        query.setQuery(QUERY);
        query.setIndex(type);
        query.setPage(page);
        query.setLimit(limit);
        return query;
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required for the disposable real-Meili gate");
        }
        return value;
    }

    private static String id(String suffix) {
        return "archfix-" + RUN_ID + "-" + suffix;
    }
}
