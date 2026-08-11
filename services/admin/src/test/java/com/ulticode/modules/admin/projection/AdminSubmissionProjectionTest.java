package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.LanguageCountDTO;
import com.ulticode.app.api.dto.ProblemAdminRowDTO;
import com.ulticode.app.api.dto.StatusCountDTO;
import com.ulticode.app.api.dto.SubmissionAdminQueryDTO;
import com.ulticode.app.api.dto.SubmissionAdminRowDTO;
import com.ulticode.app.api.service.ProblemAdminReadPort;
import com.ulticode.app.api.service.SubmissionAdminReadPort;
import com.ulticode.common.response.PageResult;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.admin.dto.AdminSubmissionVO;
import com.ulticode.modules.admin.dto.SubmissionStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAdminSubmissionProjection} &mdash; the read-side
 * deep module lifted out of AdminSubmissionServiceImpl per ADR-0011 Stage 2.
 *
 * <p>Covers the read paths that previously lived on
 * {@code AdminSubmissionServiceImplTest}: {@code getStatuses} (enum-derived),
 * {@code getLanguages} (humanised labels), {@code getStatistics} (typed
 * read-port aggregation), and {@code getSubmissions} (paginated search delegated
 * to the read port + batch user/problem enrichment). The projection no longer
 * imports {@code SubmissionMapper}; all reads route through the public
 * {@link SubmissionAdminReadPort} / {@link ProblemAdminReadPort} contracts.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultAdminSubmissionProjection")
class AdminSubmissionProjectionTest {

    @Mock private SubmissionAdminReadPort submissionReadPort;
    @Mock private AdminUserEnricher userEnricher;
    @Mock private ProblemAdminReadPort problemReadPort;

    private DefaultAdminSubmissionProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultAdminSubmissionProjection(
                submissionReadPort, userEnricher, problemReadPort,
                java.time.Clock.systemDefaultZone());
    }

    @Nested
    @DisplayName("getStatuses() — derived from SubmissionStatus enum")
    class GetStatuses {

        @Test
        @DisplayName("returns one entry per SubmissionStatus enum constant")
        void returnsOneEntryPerEnumConstant() {
            var options = projection.getStatuses();
            assertThat(options).hasSize(SubmissionStatus.values().length);
        }

        @Test
        @DisplayName("Compile Error key matches DB value (with space, not 'Compilation Error')")
        void compileError_keyMatchesDb() {
            var options = projection.getStatuses();
            var compileError = options.stream()
                .filter(o -> "COMPILE_ERROR".equals(o.getCode()))
                .findFirst().orElseThrow();
            assertThat(compileError.getKey()).isEqualTo("Compile Error");
            assertThat(compileError.getCategory()).isEqualTo("error");
        }

        @Test
        @DisplayName("Judging is included (was missing from the old hard-coded list)")
        void judging_isIncluded() {
            var options = projection.getStatuses();
            assertThat(options).extracting(o -> o.getCode())
                .contains("JUDGING", "OUTPUT_LIMIT_EXCEEDED",
                          "PRESENTATION_ERROR", "SYSTEM_ERROR");
        }
    }

    @Nested
    @DisplayName("getLanguages() — returns LanguageOption with key + label")
    class GetLanguages {

        @Test
        @DisplayName("humanises 'cpp' to 'C++' and 'javascript' to 'JavaScript'")
        void humanisesLanguageCodes() {
            when(submissionReadPort.findDistinctLanguages())
                .thenReturn(List.of("cpp", "java", "javascript", "python"));

            var languages = projection.getLanguages();
            assertThat(languages).extracting("key").containsExactly(
                "cpp", "java", "javascript", "python");
            assertThat(languages).extracting("label").containsExactly(
                "C++", "Java", "JavaScript", "Python");
        }
    }

    @Nested
    @DisplayName("getStatistics() — aggregates via the typed read port")
    class GetStatistics {

        @Test
        @DisplayName("shapes the read-port rows into the admin SubmissionStatistics VO")
        void shapesReadPortRowsIntoStats() {
            when(submissionReadPort.countAll()).thenReturn(1000L);
            when(submissionReadPort.countByStatus()).thenReturn(List.of(
                new StatusCountDTO("Accepted", 700L),
                new StatusCountDTO("Wrong Answer", 300L)
            ));
            when(submissionReadPort.countByLanguage()).thenReturn(List.of(
                new LanguageCountDTO("cpp", 400L),
                new LanguageCountDTO("python", 600L)
            ));
            when(submissionReadPort.countCreatedSince(any())).thenReturn(50L);
            when(submissionReadPort.countByStatus("Pending")).thenReturn(50L);

            SubmissionStatistics stats = projection.getStatistics();

            assertThat(stats.getTotal()).isEqualTo(1000L);
            assertThat(stats.getByStatus()).hasSize(2);
            assertThat(stats.getByStatus().get(0).getStatus()).isEqualTo("Accepted");
            assertThat(stats.getByStatus().get(0).getCount()).isEqualTo(700L);
            assertThat(stats.getByLanguage()).hasSize(2);
            assertThat(stats.getByLanguage().get(1).getLanguage()).isEqualTo("python");
            assertThat(stats.getLast24h()).isEqualTo(50L);
            assertThat(stats.getPending()).isEqualTo(50L);
        }
    }

    @Nested
    @DisplayName("getSubmissions() — delegates search to the read port and enriches")
    class GetSubmissions {

        @Test
        @DisplayName("enriches the port's page with batched user + problem data")
        void enrichesPortPageWithUserAndProblem() {
            LocalDateTime now = LocalDateTime.now();
            SubmissionAdminRowDTO s1 = row("sub-1", "u1", 100L, "cpp", "Accepted", "int main(){}", now);
            SubmissionAdminRowDTO s2 = row("sub-2", "u2", 200L, "python", "Wrong Answer", "print(1)", now);

            when(submissionReadPort.search(any(SubmissionAdminQueryDTO.class), anyInt(), anyInt()))
                .thenReturn(PageResult.of(List.of(s1, s2), 2L, 1, 10));

            when(userEnricher.enrich(anySet())).thenReturn(Map.of(
                    "u1", new AdminUserSummary("u1", "alice", "role1", "Alice", "avatar1", "alice@example.com"),
                    "u2", new AdminUserSummary("u2", "bob", "role2", "Bob", "avatar2", "bob@example.com")));

            ProblemAdminRowDTO p1 = problemRow(100L, "two-sum", "Two Sum");
            ProblemAdminRowDTO p2 = problemRow(200L, "add-two-numbers", "Add Two Numbers");
            when(problemReadPort.findProblemsByIds(anySet())).thenReturn(List.of(p1, p2));

            SubmissionAdminQueryDTO query = new SubmissionAdminQueryDTO();
            query.setPage(1);
            query.setLimit(10);

            PageResult<AdminSubmissionVO> result = projection.getSubmissions(query);

            assertThat(result.getItems()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2L);
            AdminSubmissionVO first = result.getItems().get(0);
            assertThat(first.getUsername()).isEqualTo("alice");
            assertThat(first.getProblemTitle()).isEqualTo("Two Sum");
            assertThat(first.getProblemSlug()).isEqualTo("two-sum");
            assertThat(first.getCodeLength()).isEqualTo("int main(){}".length());
            assertThat(first.getId()).isEqualTo("sub-1");
        }

        private SubmissionAdminRowDTO row(String id, String userId, Long problemId, String language,
                                          String status, String code, LocalDateTime createdAt) {
            return new SubmissionAdminRowDTO(
                    id, problemId, userId, language, status,
                    10, 5.0, createdAt, code == null ? 0 : code.length(),
                    null, null, null, null, List.of(), null, null);
        }

        private ProblemAdminRowDTO problemRow(Long id, String slug, String title) {
            return new ProblemAdminRowDTO(
                    id, slug, title, "Medium", null, null, null, null,
                    null, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null);
        }
    }
}
