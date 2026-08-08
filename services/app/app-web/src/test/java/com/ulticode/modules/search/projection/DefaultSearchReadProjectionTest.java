package com.ulticode.modules.search.projection;

import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.ulticode.app.api.dto.ForumPostIndexDTO;
import com.ulticode.app.api.service.ForumPostReadPort;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.app.api.dto.ProblemIndexDTO;
import com.ulticode.app.api.dto.UserIndexDTO;import com.ulticode.app.api.service.ProblemSearchReadPort;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;
import com.ulticode.modules.search.source.ForumSearchSource;
import com.ulticode.modules.search.source.ProblemSearchSource;
import com.ulticode.app.api.service.UserSearchReadPort;
import com.ulticode.modules.search.source.SearchSource;import com.ulticode.modules.search.source.SolutionSearchSource;
import com.ulticode.app.api.dto.SolutionIndexDTO;
import com.ulticode.app.api.service.SolutionReadPort;
import com.ulticode.modules.search.source.UserSearchSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
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

            List<ProblemIndexDTO> problems = new ArrayList<>();
            ProblemIndexDTO problem = new ProblemIndexDTO("1", "Two Sum", "two-sum", "Easy");
            problems.add(problem);

            when(problemSearchReadPort.searchForIndex(anyString(), anyInt())).thenReturn(problems);
            when(userSearchReadPort.searchForIndex(anyString(), anyInt())).thenReturn(new ArrayList<>());
            when(forumPostReadPort.searchForIndex(anyString(), anyInt())).thenReturn(new ArrayList<>());
            when(solutionReadPort.searchForIndex(anyString(), anyInt())).thenReturn(new ArrayList<>());

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            assertNotNull(response);
            assertEquals("test", response.getQuery());
            assertEquals(1, response.getTotal());
            assertEquals(1, response.getPage());
            assertEquals(20, response.getLimit());
            assertEquals(1, response.getResults().size());

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

            List<UserIndexDTO> users = new ArrayList<>();
            UserIndexDTO user = new UserIndexDTO("user-123", "testuser", "Test User", "avatar.png");
            users.add(user);

            when(userSearchReadPort.searchForIndex(anyString(), anyInt())).thenReturn(users);

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

            List<ForumPostIndexDTO> posts = new ArrayList<>();
            posts.add(new ForumPostIndexDTO("post-123", "Test Post", "This is a test post", "test-post"));

            when(forumPostReadPort.searchForIndex("test", 20)).thenReturn(posts);

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
            assertEquals("/forum/post/test-post", item.getUrl());
        }

        @Test
        @DisplayName("should search solutions when index type is SOLUTIONS")
        void shouldSearchSolutionsWhenIndexTypeIsSolutions() {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            queryDTO.setIndex(SearchIndexType.SOLUTIONS);


            List<SolutionIndexDTO> solutions = new ArrayList<>();
            solutions.add(new SolutionIndexDTO("solution-123", "Test Solution", "This is a test solution", 1L));
            when(solutionReadPort.searchForIndex(anyString(), anyInt())).thenReturn(solutions);

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

            List<ProblemIndexDTO> problems = new ArrayList<>();
            ProblemIndexDTO problem = new ProblemIndexDTO("1", "Two Sum", "two-sum", "Easy");
            problems.add(problem);

            List<UserIndexDTO> users = new ArrayList<>();
            UserIndexDTO user = new UserIndexDTO("user-123", "testuser", "Test User", "avatar.png");
            users.add(user);

            when(problemSearchReadPort.searchForIndex(anyString(), anyInt())).thenReturn(problems);
            when(userSearchReadPort.searchForIndex(anyString(), anyInt())).thenReturn(users);
            when(forumPostReadPort.searchForIndex(anyString(), anyInt())).thenReturn(new ArrayList<>());
            when(solutionReadPort.searchForIndex(anyString(), anyInt())).thenReturn(new ArrayList<>());

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            assertNotNull(response);
            assertTrue(response.getTotal() >= 2);
            assertTrue(response.getResults().size() >= 2);

            // Verify that both problems and users were searched
            verify(problemSearchReadPort).searchForIndex(anyString(), anyInt());
            verify(userSearchReadPort).searchForIndex(anyString(), anyInt());
        }

        @Test
        @DisplayName("should limit results to the specified limit")
        void shouldLimitResultsToSpecifiedLimit() {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            queryDTO.setLimit(2);

            List<ProblemIndexDTO> problems = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                ProblemIndexDTO problem = new ProblemIndexDTO("1", "Two Sum", "two-sum", "Easy");
                problems.add(problem);
            }

            List<UserIndexDTO> users = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                users.add(new UserIndexDTO("user-" + i, "testuser" + i, "Test User " + i, null));
            }

            when(problemSearchReadPort.searchForIndex(anyString(), anyInt())).thenReturn(problems);
            when(userSearchReadPort.searchForIndex(anyString(), anyInt())).thenReturn(users);
            when(forumPostReadPort.searchForIndex(anyString(), anyInt())).thenReturn(new ArrayList<>());
            when(solutionReadPort.searchForIndex(anyString(), anyInt())).thenReturn(new ArrayList<>());

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            assertEquals(2, response.getResults().size());
        }
    }

    @Nested
    @DisplayName("MeiliSearch Integration Tests")
    class MeiliSearchTests {

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
        @DisplayName("should return empty results when MeiliSearch returns errors for all indices")
        void shouldReturnEmptyResultsWhenMeiliSearchReturnsErrorsForAllIndices() throws Exception {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", meiliSearchClient);
            when(meiliSearchClient.index(anyString())).thenReturn(index);
            when(index.search(any(SearchRequest.class))).thenThrow(new RuntimeException("MeiliSearch error"));

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert - MeiliSearch errors are caught per index, so total is 0
            assertNotNull(response);
            assertEquals(0, response.getTotal());
            assertTrue(response.getResults().isEmpty());

            // Verify that database fallback was NOT called (since we didn't set up mocks for it)
            verify(problemSearchReadPort, never()).searchForIndex(anyString(), anyInt());
            verify(userSearchReadPort, never()).searchForIndex(anyString(), anyInt());
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
    }

    @Nested
    @DisplayName("Search Result Item Tests")
    class SearchResultItemTests {

        @Test
        @DisplayName("should include problem metadata")
        void shouldIncludeProblemMetadata() {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);

            List<ProblemIndexDTO> problems = new ArrayList<>();
            ProblemIndexDTO problem = new ProblemIndexDTO("1", "Two Sum", "two-sum", "Easy");
            problems.add(problem);

            when(problemSearchReadPort.searchForIndex(anyString(), anyInt())).thenReturn(problems);
            when(userSearchReadPort.searchForIndex(anyString(), anyInt())).thenReturn(new ArrayList<>());
            when(forumPostReadPort.searchForIndex(anyString(), anyInt())).thenReturn(new ArrayList<>());
            when(solutionReadPort.searchForIndex(anyString(), anyInt())).thenReturn(new ArrayList<>());

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

            List<UserIndexDTO> users = new ArrayList<>();
            UserIndexDTO user = new UserIndexDTO("user-123", "testuser", "Test User", "https://example.com/avatar.png");
            users.add(user);

            when(userSearchReadPort.searchForIndex(anyString(), anyInt())).thenReturn(users);

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            SearchResponseVO.SearchResultItem item = response.getResults().get(0);
            assertNotNull(item.getMetadata());
            assertEquals("https://example.com/avatar.png", item.getMetadata().get("avatar"));
        }
    }
}