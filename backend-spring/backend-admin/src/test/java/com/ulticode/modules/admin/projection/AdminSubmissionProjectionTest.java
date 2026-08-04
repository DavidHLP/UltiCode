package com.ulticode.modules.admin.projection;

import com.ulticode.common.response.PageResult;
import com.ulticode.modules.admin.dto.AdminSubmissionQueryDTO;
import com.ulticode.modules.admin.dto.SubmissionStatistics;
import com.ulticode.modules.admin.port.AdminSubmissionReadPort;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.app.api.dto.LanguageCountDTO;
import com.ulticode.app.api.dto.StatusCountDTO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.domain.submission.enums.SubmissionStatus;

import com.ulticode.modules.admin.projection.AdminUserEnricher;
import com.ulticode.modules.admin.projection.AdminUserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

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
 * imports {@code SubmissionMapper}; all reads route through
 * {@link AdminSubmissionReadPort}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultAdminSubmissionProjection")
class AdminSubmissionProjectionTest {

    @Mock private AdminSubmissionReadPort submissionReadPort;
    @Mock private AdminUserEnricher userEnricher;
    @Mock private ProblemMapper problemMapper;

    private DefaultAdminSubmissionProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultAdminSubmissionProjection(
                submissionReadPort, userEnricher, problemMapper,
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
            Submission s1 = new Submission();
            s1.setId("sub-1");
            s1.setUserId("u1");
            s1.setProblemId(100L);
            s1.setLanguage("cpp");
            s1.setStatus("Accepted");
            s1.setCode("int main(){}");
            s1.setCreatedAt(LocalDateTime.now());
            Submission s2 = new Submission();
            s2.setId("sub-2");
            s2.setUserId("u2");
            s2.setProblemId(200L);
            s2.setLanguage("python");
            s2.setStatus("Wrong Answer");
            s2.setCode("print(1)");
            s2.setCreatedAt(LocalDateTime.now());

            when(submissionReadPort.searchSubmissions(any(AdminSubmissionQueryDTO.class), anyInt(), anyInt()))
                .thenReturn(PageResult.of(List.of(s1, s2), 2L, 1, 10));

            when(userEnricher.enrich(anySet())).thenReturn(Map.of(
                    "u1", new AdminUserSummary("u1", "alice", "role1", "Alice", "avatar1", "alice@example.com"),
                    "u2", new AdminUserSummary("u2", "bob", "role2", "Bob", "avatar2", "bob@example.com")));

            Problem p1 = new Problem(); p1.setId(100L); p1.setTitle("Two Sum"); p1.setSlug("two-sum");
            Problem p2 = new Problem(); p2.setId(200L); p2.setTitle("Add Two Numbers"); p2.setSlug("add-two-numbers");
            when(problemMapper.selectBatchIds(any())).thenReturn(List.of(p1, p2));

            AdminSubmissionQueryDTO query = new AdminSubmissionQueryDTO();
            query.setPage(1);
            query.setLimit(10);

            var result = projection.getSubmissions(query);

            assertThat(result.getItems()).hasSize(2);
            assertThat(result.getTotal()).isEqualTo(2L);
            var first = result.getItems().get(0);
            assertThat(first.getUsername()).isEqualTo("alice");
            assertThat(first.getProblemTitle()).isEqualTo("Two Sum");
            assertThat(first.getProblemSlug()).isEqualTo("two-sum");
            assertThat(first.getCodeLength()).isEqualTo(s1.getCode().length());
            // The whole query (filters + sort + search pre-fetch) was handed to the port.
            assertThat(first.getId()).isEqualTo("sub-1");
        }
    }
}
