package com.ulticode.modules.search.projection;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.SearchRequest;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.search.dto.SearchIndexType;
import com.ulticode.modules.search.dto.SearchQueryDTO;
import com.ulticode.modules.search.dto.SearchResponseVO;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
 * <p>Migrated verbatim from the deprecated {@code SearchServiceTest}: the
 * assertion surface is unchanged because the projection preserves the
 * facade's behaviour. The {@code meiliSearchClient} field is still injected
 * via {@link ReflectionTestUtils} because the {@link Client} bean is optional
 * (only created when {@code meilisearch.enabled=true}).
 */
@ExtendWith(MockitoExtension.class)
class DefaultSearchReadProjectionTest {

    @Mock
    private ProblemMapper problemMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ForumPostMapper forumPostMapper;

    @Mock
    private SolutionMapper solutionMapper;

    @Mock
    private Client meiliSearchClient;

    @Mock
    private Index index;

    @InjectMocks
    private DefaultSearchReadProjection searchProjection;

    private SearchQueryDTO queryDTO;

    @BeforeEach
    void setUp() {
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

            List<Problem> problems = new ArrayList<>();
            Problem problem = new Problem();
            problem.setId(1L);
            problem.setTitle("Two Sum");
            problem.setSlug("two-sum");
            problem.setDifficulty("Easy");
            problem.setIsPublished(true);
            problem.setIsDeleted(false);
            problems.add(problem);

            when(problemMapper.selectList(any(QueryWrapper.class))).thenReturn(problems);
            when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
            when(forumPostMapper.searchPosts(anyString(), anyInt())).thenReturn(new ArrayList<>());
            when(solutionMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

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

            List<User> users = new ArrayList<>();
            User user = new User();
            user.setId("user-123");
            user.setUsername("testuser");
            user.setName("Test User");
            user.setAvatar("avatar.png");
            user.setIsActive(true);
            user.setIsBanned(false);
            user.setIsDeleted(0);
            users.add(user);

            when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(users);

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

            List<ForumPost> posts = new ArrayList<>();
            ForumPost post = new ForumPost();
            post.setId("post-123");
            post.setTitle("Test Post");
            post.setExcerpt("This is a test post");
            post.setPermalink("test-post");
            posts.add(post);

            when(forumPostMapper.searchPosts("test", 20)).thenReturn(posts);

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

            List<Solution> solutions = new ArrayList<>();
            Solution solution = new Solution();
            solution.setId("solution-123");
            solution.setProblemId(1L);
            solution.setTitle("Test Solution");
            solution.setSummary("This is a test solution");
            solution.setIsPublished(true);
            solution.setIsDeleted(false);
            solutions.add(solution);

            when(solutionMapper.selectList(any(QueryWrapper.class))).thenReturn(solutions);

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

            List<Problem> problems = new ArrayList<>();
            Problem problem = new Problem();
            problem.setId(1L);
            problem.setTitle("Test Problem");
            problem.setSlug("test-problem");
            problem.setIsPublished(true);
            problem.setIsDeleted(false);
            problems.add(problem);

            List<User> users = new ArrayList<>();
            User user = new User();
            user.setId("user-123");
            user.setUsername("testuser");
            user.setName("Test User");
            user.setIsActive(true);
            user.setIsBanned(false);
            user.setIsDeleted(0);
            users.add(user);

            when(problemMapper.selectList(any(QueryWrapper.class))).thenReturn(problems);
            when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(users);
            when(forumPostMapper.searchPosts(anyString(), anyInt())).thenReturn(new ArrayList<>());
            when(solutionMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            assertNotNull(response);
            assertTrue(response.getTotal() >= 2);
            assertTrue(response.getResults().size() >= 2);

            // Verify that both problems and users were searched
            verify(problemMapper).selectList(any(QueryWrapper.class));
            verify(userMapper).selectList(any(QueryWrapper.class));
        }

        @Test
        @DisplayName("should limit results to the specified limit")
        void shouldLimitResultsToSpecifiedLimit() {
            // Arrange
            ReflectionTestUtils.setField(searchProjection, "meiliSearchClient", null);
            queryDTO.setLimit(2);

            List<Problem> problems = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                Problem problem = new Problem();
                problem.setId((long) i);
                problem.setTitle("Test Problem " + i);
                problem.setSlug("test-problem-" + i);
                problem.setIsPublished(true);
                problem.setIsDeleted(false);
                problems.add(problem);
            }

            List<User> users = new ArrayList<>();
            for (int i = 1; i <= 5; i++) {
                User user = new User();
                user.setId("user-" + i);
                user.setUsername("testuser" + i);
                user.setName("Test User " + i);
                user.setIsActive(true);
                user.setIsBanned(false);
                user.setIsDeleted(0);
                users.add(user);
            }

            when(problemMapper.selectList(any(QueryWrapper.class))).thenReturn(problems);
            when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(users);
            when(forumPostMapper.searchPosts(anyString(), anyInt())).thenReturn(new ArrayList<>());
            when(solutionMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

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
            verify(problemMapper, never()).selectList(any(QueryWrapper.class));
            verify(userMapper, never()).selectList(any(QueryWrapper.class));
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

            List<Problem> problems = new ArrayList<>();
            Problem problem = new Problem();
            problem.setId(1L);
            problem.setTitle("Two Sum");
            problem.setSlug("two-sum");
            problem.setDifficulty("Easy");
            problem.setIsPublished(true);
            problem.setIsDeleted(false);
            problems.add(problem);

            when(problemMapper.selectList(any(QueryWrapper.class))).thenReturn(problems);
            when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());
            when(forumPostMapper.searchPosts(anyString(), anyInt())).thenReturn(new ArrayList<>());
            when(solutionMapper.selectList(any(QueryWrapper.class))).thenReturn(new ArrayList<>());

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

            List<User> users = new ArrayList<>();
            User user = new User();
            user.setId("user-123");
            user.setUsername("testuser");
            user.setName("Test User");
            user.setAvatar("https://example.com/avatar.png");
            user.setIsActive(true);
            user.setIsBanned(false);
            user.setIsDeleted(0);
            users.add(user);

            when(userMapper.selectList(any(QueryWrapper.class))).thenReturn(users);

            // Act
            SearchResponseVO response = searchProjection.search(queryDTO);

            // Assert
            SearchResponseVO.SearchResultItem item = response.getResults().get(0);
            assertNotNull(item.getMetadata());
            assertEquals("https://example.com/avatar.png", item.getMetadata().get("avatar"));
        }
    }
}
