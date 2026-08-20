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
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.Pagination;
import com.meilisearch.sdk.model.TaskStatus;
import com.meilisearch.sdk.model.TaskInfo;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;
import com.ulticode.modules.search.source.SearchSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

/**
 * Real MeiliSearch gate for SEARCH-003/ARCHFIX-004-004.
 *
 * <p>Run explicitly with a disposable service:
 * MEILI_E2E_HOST=http://127.0.0.1:17700 MEILI_E2E_KEY=... mvnw -Dtest=... test
 */
@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "MEILI_E2E_HOST", matches = "\\S+"),
        @EnabledIfEnvironmentVariable(named = "MEILI_E2E_KEY", matches = "\\S+")
})
class DefaultSearchReadProjectionRealMeiliIT {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String RUN_ID = UUID.randomUUID().toString().replace("-", "");
    private static final String QUERY = "archfix-real-meili-" + RUN_ID;
    private static final int PROBLEM_COUNT = 1_500;
    private static Client client;
    private static Integer previousProblemMaxTotalHits;

    @BeforeAll
    static void seedIndexes() throws Exception {
        String host = System.getenv("MEILI_E2E_HOST");
        String key = System.getenv("MEILI_E2E_KEY");
        client = new Client(new Config(host, key));
        Map<String, List<Map<String, Object>>> documents = Map.of(
                "problems", problemDocuments(),
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
            awaitSuccess(task);
        }
        Index problemIndex = client.index("problems");
        previousProblemMaxTotalHits = problemIndex.getPaginationSettings().getMaxTotalHits();
        TaskInfo paginationTask = problemIndex.updatePaginationSettings(new Pagination(PROBLEM_COUNT + 100));
        awaitSuccess(paginationTask);
    }

    @AfterAll
    static void cleanupIndexes() throws Exception {
        if (client == null) {
            return;
        }
        Map<String, List<String>> ids = Map.of(
                "problems", problemIds(),
                "users", List.of(id("u1"), id("u2")),
                "posts", List.of(id("f1")),
                "solutions", List.of(id("s1"), id("s2"), id("s3"), id("s4")));
        try {
            for (Map.Entry<String, List<String>> entry : ids.entrySet()) {
                TaskInfo task = client.index(entry.getKey()).deleteDocuments(entry.getValue());
                awaitSuccess(task);
            }
        } finally {
            if (previousProblemMaxTotalHits != null) {
                TaskInfo task = client.index("problems")
                        .updatePaginationSettings(new Pagination(previousProblemMaxTotalHits));
                awaitSuccess(task);
            }
        }
    }

    @Test
    void realMeiliSupportsSpecificAndAllIndexTotalsAndOffsets() {
        DefaultSearchReadProjection projection = projection(client);

        SearchQueryDTO specific = query(SearchIndexType.PROBLEMS, 1, 1);
        SearchResponseVO specificResponse = projection.search(specific);
        assertThat(specificResponse.getTotal()).isEqualTo(PROBLEM_COUNT);
        assertThat(specificResponse.getResults()).hasSize(1);

        SearchQueryDTO all = query(null, 751, 2);
        SearchResponseVO allResponse = projection.search(all);
        assertThat(allResponse.getTotal()).isEqualTo(PROBLEM_COUNT + 7);
        assertThat(allResponse.getResults()).hasSize(2);
        assertThat(allResponse.getResults()).extracting(SearchResponseVO.SearchResultItem::getType)
                .containsOnly("USERS");
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

    private static List<Map<String, Object>> problemDocuments() {
        List<Map<String, Object>> documents = new ArrayList<>(PROBLEM_COUNT);
        for (int i = 0; i < PROBLEM_COUNT; i++) {
            documents.add(Map.of(
                    "id", id("p" + i),
                    "title", QUERY + " problem " + i,
                    "slug", id("p" + i)));
        }
        return documents;
    }

    private static List<String> problemIds() {
        List<String> ids = new ArrayList<>(PROBLEM_COUNT);
        for (int i = 0; i < PROBLEM_COUNT; i++) {
            ids.add(id("p" + i));
        }
        return ids;
    }

    private static void awaitSuccess(TaskInfo taskInfo) throws Exception {
        int taskUid = taskInfo.getTaskUid();
        client.waitForTask(taskUid);
        assertThat(client.getTask(taskUid).getStatus()).isEqualTo(TaskStatus.SUCCEEDED);
    }

    private static String id(String suffix) {
        return "archfix-" + RUN_ID + "-" + suffix;
    }
}
