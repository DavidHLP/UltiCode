package com.ulticode.modules.admin.port.adapter;

import com.ulticode.modules.admin.port.AdminCommentReadPort;
import com.ulticode.modules.forum.entity.ForumPost;
import com.ulticode.modules.forum.mapper.ForumPostMapper;
import com.ulticode.modules.solution.entity.Solution;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * Unit tests for {@link AdminCommentReadAdapter}.
 *
 * <p>The adapter's single responsibility is turning the cross-module
 * User / ForumPost / Solution mappers into typed views
 * ({@link AdminCommentReadPort.AuthorSummary} + title strings) for the admin
 * comment read path. These tests pin three contracts the deep module owes
 * its callers: empty-input short-circuit (no mapper call), entity→view
 * coercion keyed by id, and null-value tolerance (titles preserved rather
 * than dropped — {@link java.util.stream.Collectors#toMap} would NPE here).
 * With {@code AdminCommentServiceImpl} no longer touching these mappers
 * directly, this is the only place where that boundary is exercised.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminCommentReadAdapter")
class AdminCommentReadAdapterTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private ForumPostMapper forumPostMapper;

    @Mock
    private SolutionMapper solutionMapper;
    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private AdminCommentReadAdapter adapter;

    @Nested
    @DisplayName("empty-input short-circuit")
    class EmptyInput {

        @Test
        @DisplayName("empty user-id set returns empty map without touching UserMapper")
        void emptyUserIds_noMapperCall() {
            assertThat(adapter.findAuthorSummariesByIds(Set.of())).isEmpty();
            verifyNoInteractions(userMapper);
        }

        @Test
        @DisplayName("empty post-id set returns empty map without touching ForumPostMapper")
        void emptyPostIds_noMapperCall() {
            assertThat(adapter.findForumPostTitlesByIds(Set.of())).isEmpty();
            verifyNoInteractions(forumPostMapper);
        }

        @Test
        @DisplayName("empty solution-id set returns empty map without touching SolutionMapper")
        void emptySolutionIds_noMapperCall() {
            assertThat(adapter.findSolutionTitlesByIds(Set.of())).isEmpty();
            verifyNoInteractions(solutionMapper);
        }
    }

    @Nested
    @DisplayName("author-summary coercion")
    class AuthorCoercion {

        @Test
        @DisplayName("users are coerced to AuthorSummary keyed by id, null avatar preserved")
        void usersMappedToSummary() {
            User u1 = new User();
            u1.setId("u1");
            u1.setUsername("alice");
            u1.setAvatar("https://cdn.example.com/a.png");
            User u2 = new User();
            u2.setId("u2");
            u2.setUsername("bob");
            u2.setAvatar(null);
            when(userMapper.selectBatchIds(Set.of("u1", "u2"))).thenReturn(List.of(u1, u2));

            Map<String, AdminCommentReadPort.AuthorSummary> result =
                    adapter.findAuthorSummariesByIds(Set.of("u1", "u2"));

            assertThat(result).hasSize(2);
            assertThat(result.get("u1").username()).isEqualTo("alice");
            assertThat(result.get("u1").avatar()).isEqualTo("https://cdn.example.com/a.png");
            assertThat(result.get("u2").username()).isEqualTo("bob");
            assertThat(result.get("u2").avatar()).isNull();
        }

        @Test
        @DisplayName("missing user ids are absent so the caller coerces null author")
        void missingUserIdsAbsent() {
            User u1 = new User();
            u1.setId("u1");
            u1.setUsername("alice");
            when(userMapper.selectBatchIds(Set.of("u1", "ghost"))).thenReturn(List.of(u1));

            Map<String, AdminCommentReadPort.AuthorSummary> result =
                    adapter.findAuthorSummariesByIds(Set.of("u1", "ghost"));

            assertThat(result).containsKey("u1");
            assertThat(result).doesNotContainKey("ghost");
        }
    }

    @Nested
    @DisplayName("title coercion (null-value tolerance)")
    class TitleCoercion {

        @Test
        @DisplayName("null forum-post title is preserved, not dropped")
        void nullPostTitlePreserved() {
            ForumPost p1 = new ForumPost();
            p1.setId("p1");
            p1.setTitle("Hello");
            ForumPost p2 = new ForumPost();
            p2.setId("p2");
            p2.setTitle(null);
            when(forumPostMapper.selectBatchIds(Set.of("p1", "p2"))).thenReturn(List.of(p1, p2));

            Map<String, String> result = adapter.findForumPostTitlesByIds(Set.of("p1", "p2"));

            assertThat(result).hasSize(2);
            assertThat(result.get("p1")).isEqualTo("Hello");
            assertThat(result).containsKey("p2");
            assertThat(result.get("p2")).isNull();
        }

        @Test
        @DisplayName("solution titles mapped keyed by id")
        void solutionTitlesMapped() {
            Solution s1 = new Solution();
            s1.setId("s1");
            s1.setTitle("Two Sum");
            when(solutionMapper.selectBatchIds(Set.of("s1"))).thenReturn(List.of(s1));

            Map<String, String> result = adapter.findSolutionTitlesByIds(Set.of("s1"));

            assertThat(result.get("s1")).isEqualTo("Two Sum");
        }
    }
}
