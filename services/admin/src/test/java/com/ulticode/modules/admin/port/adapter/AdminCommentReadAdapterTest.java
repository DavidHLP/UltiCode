package com.ulticode.modules.admin.port.adapter;

import com.ulticode.app.api.service.AdminForumReadPort;
import com.ulticode.app.api.service.SolutionReadPort;
import com.ulticode.modules.admin.port.AdminCommentReadPort;
import com.ulticode.modules.admin.projection.AdminUserEnricher;
import com.ulticode.modules.admin.projection.AdminUserSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdminCommentReadAdapter}.
 *
 * <p>The adapter's single responsibility is turning the cross-module
 * read seams (User / Forum / Solution) into typed views
 * ({@link AdminCommentReadPort.AuthorSummary} + title strings) for the
 * admin comment read path. These tests pin three contracts the deep
 * module owes its callers: empty-input short-circuit (no underlying seam
 * call), view coercion keyed by id, and null-value tolerance (titles
 * preserved rather than dropped). With {@code AdminCommentServiceImpl}
 * no longer touching the underlying mappers directly, this is the only
 * place where that boundary is exercised.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminCommentReadAdapter")
class AdminCommentReadAdapterTest {

    @Mock
    private AdminUserEnricher userEnricher;

    @Mock
    private AdminForumReadPort adminForumReadPort;

    @Mock
    private SolutionReadPort solutionReadPort;

    @InjectMocks
    private AdminCommentReadAdapter adapter;

    @Nested
    @DisplayName("empty-input short-circuit")
    class EmptyInput {

        @Test
        @DisplayName("empty user-id set never touches AdminUserEnricher")
        void emptyUserIds() {
            assertThat(adapter.findAuthorSummariesByIds(Set.of())).isEmpty();
        }

        @Test
        @DisplayName("empty post-id set still delegates to AdminForumReadPort")
        void emptyPostIds() {
            when(adminForumReadPort.findPostTitlesByIds(Set.of())).thenReturn(Map.of());
            assertThat(adapter.findForumPostTitlesByIds(Set.of())).isEmpty();
            verify(adminForumReadPort).findPostTitlesByIds(Set.of());
        }

        @Test
        @DisplayName("empty solution-id set still delegates to SolutionReadPort")
        void emptySolutionIds() {
            when(solutionReadPort.findTitlesByIds(Set.of())).thenReturn(Map.of());
            assertThat(adapter.findSolutionTitlesByIds(Set.of())).isEmpty();
            verify(solutionReadPort).findTitlesByIds(Set.of());
        }
    }

    @Nested
    @DisplayName("author-summary coercion")
    class AuthorCoercion {

        @Test
        @DisplayName("users map to AuthorSummary keyed by account id")
        void usersCoerced() {
            AdminUserSummary alice = new AdminUserSummary("u1", "alice", null, null, "https://cdn.example.com/a.png", null);
            AdminUserSummary bob = new AdminUserSummary("u2", "bob", null, null, null, null);
            when(userEnricher.enrich(Set.of("u1", "u2"))).thenReturn(Map.of("u1", alice, "u2", bob));

            Map<String, AdminCommentReadPort.AuthorSummary> result =
                    adapter.findAuthorSummariesByIds(Set.of("u1", "u2"));

            assertThat(result).hasSize(2);
            assertThat(result.get("u1").username()).isEqualTo("alice");
            assertThat(result.get("u1").avatar()).isEqualTo("https://cdn.example.com/a.png");
            assertThat(result.get("u2").username()).isEqualTo("bob");
            assertThat(result.get("u2").avatar()).isNull();
        }

        @Test
        @DisplayName("ids without a matching user are absent from the map")
        void missingUserAbsent() {
            AdminUserSummary alice = new AdminUserSummary("u1", "alice", null, null, null, null);
            when(userEnricher.enrich(Set.of("u1", "ghost"))).thenReturn(Map.of("u1", alice));

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
        @DisplayName("null forum-post title preserved via AdminForumReadPort")
        void nullPostTitlePreserved() {
            Map<String, String> titles = new HashMap<>();
            titles.put("p1", "Hello");
            titles.put("p2", null);
            when(adminForumReadPort.findPostTitlesByIds(Set.of("p1", "p2"))).thenReturn(titles);

            Map<String, String> result = adapter.findForumPostTitlesByIds(Set.of("p1", "p2"));

            assertThat(result).hasSize(2);
            assertThat(result.get("p1")).isEqualTo("Hello");
            assertThat(result).containsKey("p2");
            assertThat(result.get("p2")).isNull();
        }

        @Test
        @DisplayName("solution titles mapped keyed by id via SolutionReadPort")
        void solutionTitlesMapped() {
            when(solutionReadPort.findTitlesByIds(Set.of("s1"))).thenReturn(Map.of("s1", "Two Sum"));

            Map<String, String> result = adapter.findSolutionTitlesByIds(Set.of("s1"));

            assertThat(result.get("s1")).isEqualTo("Two Sum");
        }
    }
}
