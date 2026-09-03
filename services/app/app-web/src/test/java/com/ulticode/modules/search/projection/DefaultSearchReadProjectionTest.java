package com.ulticode.modules.search.projection;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.meilisearch.sdk.model.Pagination;
import com.meilisearch.sdk.model.SearchResult;
import com.meilisearch.sdk.model.SearchResultPaginated;
import com.ulticode.app.api.dto.ForumPostIndexDTO;
import com.ulticode.app.api.service.ForumPostReadPort;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.app.api.dto.ProblemIndexDTO;
import com.ulticode.app.api.dto.UserIndexDTO;
import com.ulticode.modules.problem.port.ProblemSearchReadPort;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;
import com.ulticode.modules.search.dto.SearchReadSemantics;
import com.ulticode.modules.search.config.SearchReadProperties;
import com.ulticode.modules.search.source.ForumSearchSource;
import com.ulticode.modules.search.source.ProblemSearchSource;
import com.ulticode.app.api.service.UserSearchReadPort;
import com.ulticode.modules.search.source.SearchSource;import com.ulticode.modules.search.source.SolutionSearchSource;
import com.ulticode.app.api.dto.SolutionIndexDTO;
import com.ulticode.app.api.service.SolutionReadPort;
import com.ulticode.modules.search.source.UserSearchSource;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DefaultSearchReadProjection}.
 *
 * <p>The test wires the four per-source {@link SearchSource} adapters by
 * hand (with mocked mappers) and injects them into the projection as a
 * {@code List<SearchSource>}. This preserves the original assertion
 * surface while exercising the new per-source decomposition end-to-end.
 */
@ExtendWith(MockitoExtension.class)
class DefaultSearchReadProjectionTest {

    @Mock private ProblemSearchReadPort problemSearchReadPort;

    @Mock
    private UserSearchReadPort userSearchReadPort;

    @Mock
    private ForumPostReadPort forumPostReadPort;
    @Mock
    private SolutionReadPort solutionReadPort;

    @Mock
    private Client meiliSearchClient;

    @Mock
    private Index index;

    @Mock
    private SearchResult searchable;

    @Mock
    private SearchResultPaginated countResult;

    private DefaultSearchReadProjection searchProjection;

    private SearchQueryDTO queryDTO;

    @BeforeEach
    void setUp() {
        List<SearchSource> sources = List.of(
                new ProblemSearchSource(problemSearchReadPort),
                new UserSearchSource(userSearchReadPort),
                new ForumSearchSource(forumPostReadPort),
                new SolutionSearchSource(solutionReadPort)
        );
        searchProjection = new DefaultSearchReadProjection(sources);

        queryDTO = new SearchQueryDTO();
        queryDTO.setQuery("test");
        queryDTO.setPage(1);
        queryDTO.setLimit(20);
    }

    @Nested
    @DisplayName("Database Fallback Search Tests")
    class DatabaseFallbackTests {

        @Test
        @DisplayName("should search problems when MeiliSearch is not available")
        void shouldSearchProblemsWhenMeiliSearchNotAvailable() {
            // Arrange - MeiliSearch client is not set
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            when(problemSearchReadPort.countForIndex(anyString())).thenReturn(1L);

            List<ProblemIndexDTO> problems = new ArrayList<>();
            ProblemIndexDTO problem = new ProblemIndexDTO("1", "Two Sum", "two-sum", "Easy");
            problems.add(problem);

            when(problemSearchReadPort.searchForIndex(anyString(), anyInt(), anyInt())).thenReturn(problems);

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            assertNotNull(response);
            assertEquals("test", response.getQuery());
            assertEquals(1, response.getTotal());
            assertEquals(1, response.getPage());
            assertEquals(20, response.getLimit());
            assertEquals(1, response.getResults().size());
            assertThat(response.getSemantics()).isEqualTo(new SearchReadSemantics(
                    "DATABASE", "DATABASE", "REALTIME", "SOURCE_ID_ASC", "EXACT", false));

            SearchResponseVO.SearchResultItem item = response.getResults().get(0);
            assertEquals("1", item.getId());
            assertEquals("PROBLEMS", item.getType());
            assertEquals("Two Sum", item.getTitle());
            assertEquals("/problems/two-sum", item.getUrl());
        }

        @Test
        @DisplayName("should search users when index type is USERS")
        void shouldSearchUsersWhenIndexTypeIsUsers() {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            queryDTO.setIndex(SearchIndexType.USERS);
            when(userSearchReadPort.countForIndex(anyString())).thenReturn(1L);

            List<UserIndexDTO> users = new ArrayList<>();
            UserIndexDTO user = new UserIndexDTO("user-123", "testuser", "Test User", "avatar.png");
            users.add(user);
            when(userSearchReadPort.searchForIndex(anyString(), anyInt(), anyInt()))
                    .thenReturn(users);

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            assertNotNull(response);
            assertEquals(1, response.getTotal());
            assertEquals(1, response.getResults().size());

            SearchResponseVO.SearchResultItem item = response.getResults().get(0);
            assertEquals("user-123", item.getId());
            assertEquals("USERS", item.getType());
            assertEquals("testuser", item.getTitle());
            assertEquals("/u/testuser", item.getUrl());
        }

        @Test
        @DisplayName("should search posts when index type is POSTS")
        void shouldSearchPostsWhenIndexTypeIsPosts() {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            queryDTO.setIndex(SearchIndexType.POSTS);
            when(forumPostReadPort.countForIndex(anyString())).thenReturn(1L);

            List<ForumPostIndexDTO> posts = new ArrayList<>();
            posts.add(new ForumPostIndexDTO("post-123", "Test Post", "This is a test post", "test-post"));

            when(forumPostReadPort.searchForIndex("test", 0, 20)).thenReturn(posts);

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            assertNotNull(response);
            assertEquals(1, response.getTotal());
            assertEquals(1, response.getResults().size());

            SearchResponseVO.SearchResultItem item = response.getResults().get(0);
            assertEquals("post-123", item.getId());
            assertEquals("POSTS", item.getType());
            assertEquals("Test Post", item.getTitle());
            assertEquals("/forum/detailed/post-123", item.getUrl());
        }

        @Test
        @DisplayName("should pass the requested offset to a specific source")
        void shouldPassOffsetToSpecificSource() {
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            queryDTO.setIndex(SearchIndexType.POSTS);
            queryDTO.setPage(2);
            when(forumPostReadPort.countForIndex(anyString())).thenReturn(21L);

            when(forumPostReadPort.searchForIndex("test", 20, 20))
                    .thenReturn(List.of(new ForumPostIndexDTO(
                            "post-2", "Second page", "Excerpt", "second-page")));

            SearchResponseVO response = searchProjection.search(queryDTO);

            assertThat(response.getResults()).singleElement()
                    .extracting(SearchResponseVO.SearchResultItem::getId)
                    .isEqualTo("post-2");
            verify(forumPostReadPort).searchForIndex("test", 20, 20);
        }

        @Test
        @DisplayName("should return empty out-of-range rows with the exact total")
        void shouldReturnEmptyRowsWhenSpecificOffsetIsOutOfRange() {
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            queryDTO.setIndex(SearchIndexType.PROBLEMS);
            queryDTO.setPage(2);
            when(problemSearchReadPort.countForIndex(anyString())).thenReturn(3L);

            SearchResponseVO response = searchProjection.search(queryDTO);

            assertThat(response.getTotal()).isEqualTo(3);
            assertThat(response.getResults()).isEmpty();
            verify(problemSearchReadPort, never()).searchForIndex(anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should search solutions when index type is SOLUTIONS")
        void shouldSearchSolutionsWhenIndexTypeIsSolutions() {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            queryDTO.setIndex(SearchIndexType.SOLUTIONS);
            when(solutionReadPort.countForIndex(anyString())).thenReturn(1L);


            List<SolutionIndexDTO> solutions = new ArrayList<>();
            solutions.add(new SolutionIndexDTO("solution-123", "Test Solution", "This is a test solution", 1L));
            when(solutionReadPort.searchForIndex(anyString(), anyInt(), anyInt())).thenReturn(solutions);

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            assertNotNull(response);
            assertEquals(1, response.getTotal());
            assertEquals(1, response.getResults().size());

            SearchResponseVO.SearchResultItem item = response.getResults().get(0);
            assertEquals("solution-123", item.getId());
            assertEquals("SOLUTIONS", item.getType());
            assertEquals("Test Solution", item.getTitle());
            assertEquals("/problems/1/solutions/solution-123", item.getUrl());
        }

        @Test
        @DisplayName("should search all indices when index type is not specified")
        void shouldSearchAllIndicesWhenIndexTypeNotSpecified() {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            // index is null by default
            when(problemSearchReadPort.countForIndex(anyString())).thenReturn(1L);
            when(userSearchReadPort.countForIndex(anyString())).thenReturn(1L);

            List<ProblemIndexDTO> problems = new ArrayList<>();
            ProblemIndexDTO problem = new ProblemIndexDTO("1", "Two Sum", "two-sum", "Easy");
            problems.add(problem);

            List<UserIndexDTO> users = new ArrayList<>();
            UserIndexDTO user = new UserIndexDTO("user-123", "testuser", "Test User", "avatar.png");
            users.add(user);

            when(problemSearchReadPort.searchForIndex(anyString(), anyInt(), anyInt())).thenReturn(problems);
            when(userSearchReadPort.searchForIndex(anyString(), anyInt(), anyInt())).thenReturn(users);
            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            assertNotNull(response);
            assertTrue(response.getTotal() >= 2);
            assertTrue(response.getResults().size() >= 2);

            // Verify that both problems and users were searched
            verify(problemSearchReadPort).searchForIndex(anyString(), anyInt(), anyInt());
            verify(userSearchReadPort).searchForIndex(anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should map a global offset across source boundaries and keep exact total")
        void shouldMapGlobalOffsetAcrossSources() {
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            queryDTO.setPage(2);
            queryDTO.setLimit(2);

            when(problemSearchReadPort.countForIndex(anyString())).thenReturn(3L);
            when(userSearchReadPort.countForIndex(anyString())).thenReturn(2L);
            when(forumPostReadPort.countForIndex(anyString())).thenReturn(1L);
            when(solutionReadPort.countForIndex(anyString())).thenReturn(0L);
            when(problemSearchReadPort.searchForIndex("test", 2, 2))
                    .thenReturn(List.of(new ProblemIndexDTO("p-3", "Third", "third", "Easy")));
            when(userSearchReadPort.searchForIndex("test", 0, 1))
                    .thenReturn(List.of(new UserIndexDTO("u-1", "alice", "Alice", null)));

            SearchResponseVO response = searchProjection.search(queryDTO);

            assertThat(response.getTotal()).isEqualTo(6);
            assertThat(response.getResults())
                    .extracting(SearchResponseVO.SearchResultItem::getId)
                    .containsExactly("p-3", "u-1");
            verify(problemSearchReadPort).searchForIndex("test", 2, 2);
            verify(userSearchReadPort).searchForIndex("test", 0, 1);
        }

        @Test
        @DisplayName("should limit results to the specified limit")
        void shouldLimitResultsToSpecifiedLimit() {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            queryDTO.setLimit(2);
            when(problemSearchReadPort.countForIndex(anyString())).thenReturn(5L);
            when(userSearchReadPort.countForIndex(anyString())).thenReturn(5L);

            List<ProblemIndexDTO> problems = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                ProblemIndexDTO problem = new ProblemIndexDTO("1", "Two Sum", "two-sum", "Easy");
                problems.add(problem);
            }

            List<UserIndexDTO> users = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                users.add(new UserIndexDTO("user-" + i, "testuser" + i, "Test User " + i, null));
            }

            when(problemSearchReadPort.searchForIndex(anyString(), anyInt(), anyInt())).thenReturn(problems);

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            assertEquals(2, response.getResults().size());
        }

        @Test
        @DisplayName("database mode ignores a configured MeiliSearch client")
        void databaseModeDoesNotTouchMeiliSearch() {
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", meiliSearchClient);
            queryDTO.setIndex(SearchIndexType.PROBLEMS);
            when(problemSearchReadPort.countForIndex("test")).thenReturn(1L);
            when(problemSearchReadPort.searchForIndex("test", 0, 20)).thenReturn(
                    List.of(new ProblemIndexDTO("db-1", "Database", "database", "Easy")));

            SearchResponseVO response = searchProjection.search(queryDTO);

            assertThat(response.getResults()).singleElement()
                    .extracting(SearchResponseVO.SearchResultItem::getId)
                    .isEqualTo("db-1");
            verifyNoInteractions(meiliSearchClient);
        }
    }

    @Nested
    @DisplayName("MeiliSearch Integration Tests")
    class MeiliSearchTests {

        @BeforeEach
        void useIndexedReadMode() {
            searchProjection.setReadMode(SearchReadProperties.Mode.INDEXED);
            searchProjection.setWorkerEnabled(true);
        }

        @Test
        @DisplayName("should return true when MeiliSearch client is available")
        void shouldReturnTrueWhenMeiliSearchAvailable() {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", meiliSearchClient);

            // Act
            boolean available = searchProjection.isMeiliSearchAvailable();

            // Assert
            assertTrue(available);
        }

        @Test
        @DisplayName("should return false when MeiliSearch client is not available")
        void shouldReturnFalseWhenMeiliSearchNotAvailable() {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);

            // Act
            boolean available = searchProjection.isMeiliSearchAvailable();

            // Assert
            assertFalse(available);
        }

        @Test
        @DisplayName("indexed mode fails closed when MeiliSearch is unavailable")
        void indexedModeFailsClosedWithoutFallback() {
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            searchProjection.setFallbackToDatabase(false);

            assertThatThrownBy(() -> searchProjection.search(queryDTO))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Indexed search is enabled");
        }

        @Test
        @DisplayName("indexed mode fails closed when the DevStack worker is disabled")
        void indexedModeFailsClosedWhenWorkerIsDisabled() {
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", meiliSearchClient);
            searchProjection.setWorkerEnabled(false);
            searchProjection.setFallbackToDatabase(false);

            assertThatThrownBy(() -> searchProjection.search(queryDTO))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Indexed search is enabled");
        }

        @Test
        @DisplayName("should fall back to the database when MeiliSearch fails")
        void shouldFallBackToDatabaseWhenMeiliSearchFails() throws Exception {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", meiliSearchClient);
            searchProjection.setFallbackToDatabase(true);
            queryDTO.setIndex(SearchIndexType.PROBLEMS);
            when(meiliSearchClient.index(anyString())).thenReturn(index);
            when(index.search(any(SearchRequest.class))).thenThrow(new RuntimeException("MeiliSearch error"));
            when(problemSearchReadPort.countForIndex(anyString())).thenReturn(1L);
            when(problemSearchReadPort.searchForIndex(anyString(), anyInt(), anyInt()))
                    .thenReturn(List.of(new ProblemIndexDTO("1", "Two Sum", "two-sum", "Easy")));

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert - one backend failure falls back for the whole request
            assertNotNull(response);
            assertEquals(1, response.getTotal());
            assertEquals("1", response.getResults().get(0).getId());
            assertThat(response.getSemantics()).isEqualTo(new SearchReadSemantics(
                    "INDEXED", "DATABASE", "REALTIME", "SOURCE_ID_ASC", "EXACT", true));
            verify(problemSearchReadPort).searchForIndex(anyString(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("should use the post id in MeiliSearch forum URLs")
        void shouldUsePostIdInMeiliSearchForumUrls() throws Exception {
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", meiliSearchClient);
            queryDTO.setIndex(SearchIndexType.POSTS);

            HashMap<String, Object> hit = new HashMap<>();
            hit.put("id", "post-123");
            hit.put("title", "Test Post");
            hit.put("permalink", "test-post");
            ArrayList<HashMap<String, Object>> hits = new ArrayList<>();
            hits.add(hit);

            when(meiliSearchClient.index(anyString())).thenReturn(index);
            when(index.search(any(SearchRequest.class))).thenReturn(countResult, searchable);
            when(index.getPaginationSettings()).thenReturn(new Pagination(1_000));
            when(countResult.getTotalHits()).thenReturn(7);
            when(searchable.getHits()).thenReturn(hits);

            SearchResponseVO response = searchProjection.search(queryDTO);

            assertEquals(7, response.getTotal());
            assertEquals(1, response.getResults().size());
            assertEquals("/forum/detailed/post-123", response.getResults().get(0).getUrl());
            ArgumentCaptor<SearchRequest> requests = ArgumentCaptor.forClass(SearchRequest.class);
            verify(index, times(2)).search(requests.capture());
            assertEquals(1, requests.getAllValues().get(0).getPage());
            assertEquals(0, requests.getAllValues().get(0).getHitsPerPage());
            assertEquals(0, requests.getAllValues().get(1).getOffset());
            assertEquals(20, requests.getAllValues().get(1).getLimit());
        }

        @Test
        @DisplayName("should fall back when MeiliSearch exact total reaches its configured cap")
        void shouldFallBackWhenExactTotalReachesConfiguredCap() throws Exception {
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", meiliSearchClient);
            searchProjection.setFallbackToDatabase(true);
            queryDTO.setIndex(SearchIndexType.PROBLEMS);
            when(meiliSearchClient.index(anyString())).thenReturn(index);
            when(index.search(any(SearchRequest.class))).thenReturn(countResult);
            when(index.getPaginationSettings()).thenReturn(new Pagination(1_000));
            when(countResult.getTotalHits()).thenReturn(1_000);
            when(problemSearchReadPort.countForIndex("test")).thenReturn(1_500L);
            when(problemSearchReadPort.searchForIndex("test", 0, 20)).thenReturn(List.of());

            SearchResponseVO response = searchProjection.search(queryDTO);

            assertEquals(1_500, response.getTotal());
            verify(index, times(1)).search(any(SearchRequest.class));
            verify(problemSearchReadPort).searchForIndex("test", 0, 20);
        }

        @Test
        @DisplayName("should fetch MeiliSearch hits only from indexes contributing to the requested page")
        void shouldFetchHitsOnlyFromContributingIndexes() throws Exception {
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", meiliSearchClient);
            queryDTO.setPage(4);
            queryDTO.setLimit(2);
            SearchResultPaginated problemCount = mock(SearchResultPaginated.class);
            SearchResultPaginated userCount = mock(SearchResultPaginated.class);
            SearchResultPaginated postCount = mock(SearchResultPaginated.class);
            SearchResultPaginated solutionCount = mock(SearchResultPaginated.class);
            when(problemCount.getTotalHits()).thenReturn(3);
            when(userCount.getTotalHits()).thenReturn(2);
            when(postCount.getTotalHits()).thenReturn(1);
            when(solutionCount.getTotalHits()).thenReturn(4);
            when(meiliSearchClient.index(anyString())).thenReturn(index);
            when(index.getPaginationSettings()).thenReturn(new Pagination(1_000));
            when(index.search(any(SearchRequest.class)))
                    .thenReturn(problemCount, userCount, postCount, solutionCount, searchable);
            when(searchable.getHits()).thenReturn(new ArrayList<>(List.of(
                    new HashMap<>(java.util.Map.of("id", "s1", "title", "Solution 1")),
                    new HashMap<>(java.util.Map.of("id", "s2", "title", "Solution 2")))));

            SearchResponseVO response = searchProjection.search(queryDTO);

            assertEquals(10, response.getTotal());
            assertEquals(2, response.getResults().size());
            ArgumentCaptor<SearchRequest> requests = ArgumentCaptor.forClass(SearchRequest.class);
            verify(index, times(5)).search(requests.capture());
            assertThat(requests.getAllValues().subList(0, 4))
                    .allSatisfy(request -> assertEquals(0, request.getHitsPerPage()));
            assertEquals(0, requests.getAllValues().get(4).getOffset());
            assertEquals(2, requests.getAllValues().get(4).getLimit());
        }

        @Test
        @DisplayName("should ignore the worker _aggregateVersion field and unknown hit keys")
        void shouldIgnoreAggregateVersionFieldAndUnknownKeys() throws Exception {
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", meiliSearchClient);
            queryDTO.setIndex(SearchIndexType.PROBLEMS);

            HashMap<String, Object> hit = new HashMap<>();
            hit.put("id", "1");
            hit.put("title", "Two Sum");
            hit.put("slug", "two-sum");
            hit.put("difficulty", "Easy");
            // SEARCH-003 worker writes this field into every document (DEC-016);
            // the read path must ignore it and any forward-compatible keys.
            hit.put("_aggregateVersion", 1_786_874_400_000L);
            hit.put("someFutureField", "x");
            ArrayList<HashMap<String, Object>> hits = new ArrayList<>();
            hits.add(hit);

            when(meiliSearchClient.index(anyString())).thenReturn(index);
            when(index.search(any(SearchRequest.class))).thenReturn(countResult, searchable);
            when(index.getPaginationSettings()).thenReturn(new Pagination(1_000));
            when(countResult.getTotalHits()).thenReturn(1);
            when(searchable.getHits()).thenReturn(hits);

            SearchResponseVO response = searchProjection.search(queryDTO);

            assertEquals(1, response.getResults().size());
            SearchResponseVO.SearchResultItem item = response.getResults().get(0);
            assertEquals("Two Sum", item.getTitle());
            assertEquals("two-sum", item.getMetadata().get("slug"));
            assertEquals("Easy", item.getMetadata().get("difficulty"));
            assertFalse(item.getMetadata().containsKey("_aggregateVersion"));
            assertFalse(item.getMetadata().containsKey("someFutureField"));
        }
    }

    @Nested
    @DisplayName("Query Validation Tests")
    class QueryValidationTests {

        @Test
        @DisplayName("should calculate correct offset")
        void shouldCalculateCorrectOffset() {
            // Arrange
            queryDTO.setPage(1);
            queryDTO.setLimit(20);

            // Act & Assert
            assertEquals(0, queryDTO.getOffset());

            queryDTO.setPage(2);
            assertEquals(20, queryDTO.getOffset());

            queryDTO.setPage(3);
            queryDTO.setLimit(10);
            assertEquals(20, queryDTO.getOffset());

            queryDTO.setPage(Integer.MAX_VALUE);
            queryDTO.setLimit(100);
            assertEquals(Integer.MAX_VALUE, queryDTO.getOffset());
        }

        @Test
        @DisplayName("should use default values when not specified")
        void shouldUseDefaultValues() {
            // Arrange
            SearchQueryDTO dto = new SearchQueryDTO();
            dto.setQuery("test");

            // Act & Assert
            assertEquals(1, dto.getPage());
            assertEquals(20, dto.getLimit());
        }

        @Test
        @DisplayName("should reject explicit null pagination values")
        void shouldRejectNullPaginationValues() {
            SearchQueryDTO dto = new SearchQueryDTO();
            dto.setQuery("test");
            dto.setPage(null);
            dto.setLimit(null);

            try (var factory = Validation.buildDefaultValidatorFactory()) {
                assertThat(factory.getValidator().validate(dto))
                        .extracting(violation -> violation.getPropertyPath().toString())
                        .contains("page", "limit");
            }
        }
    }

    @Nested
    @DisplayName("Search Result Item Tests")
    class SearchResultItemTests {

        @Test
        @DisplayName("should include problem metadata")
        void shouldIncludeProblemMetadata() {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            when(problemSearchReadPort.countForIndex(anyString())).thenReturn(1L);

            List<ProblemIndexDTO> problems = new ArrayList<>();
            ProblemIndexDTO problem = new ProblemIndexDTO("1", "Two Sum", "two-sum", "Easy");
            problems.add(problem);

            when(problemSearchReadPort.searchForIndex(anyString(), anyInt(), anyInt())).thenReturn(problems);

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            SearchResponseVO.SearchResultItem item = response.getResults().get(0);
            assertNotNull(item.getMetadata());
            assertEquals("two-sum", item.getMetadata().get("slug"));
            assertEquals("Easy", item.getMetadata().get("difficulty"));
        }

        @Test
        @DisplayName("should include user avatar in metadata")
        void shouldIncludeUserAvatarInMetadata() {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            queryDTO.setIndex(SearchIndexType.USERS);
            when(userSearchReadPort.countForIndex(anyString())).thenReturn(1L);

            List<UserIndexDTO> users = new ArrayList<>();
            UserIndexDTO user = new UserIndexDTO("user-123", "testuser", "Test User", "https://example.com/avatar.png");
            users.add(user);

            when(userSearchReadPort.searchForIndex(anyString(), anyInt(), anyInt())).thenReturn(users);

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            SearchResponseVO.SearchResultItem item = response.getResults().get(0);
            assertNotNull(item.getMetadata());
            assertEquals("https://example.com/avatar.png", item.getMetadata().get("avatar"));
        }
    }
}
