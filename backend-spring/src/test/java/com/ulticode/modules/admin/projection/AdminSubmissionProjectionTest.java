package com.ulticode.modules.admin.projection;

import com.ulticode.modules.admin.dto.SubmissionStatistics;
import com.ulticode.modules.admin.port.AdminSubmissionReadPort;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.dto.LanguageCountDTO;
import com.ulticode.modules.submission.dto.StatusCountDTO;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultAdminSubmissionProjection} &mdash; the read-side
 * deep module lifted out of AdminSubmissionServiceImpl per ADR-0011 Stage 2.
 *
 * <p>Covers the read paths that previously lived on
 * {@code AdminSubmissionServiceImplTest}: {@code getStatuses} (enum-derived),
 * {@code getLanguages} (humanised labels) and {@code getStatistics} (typed
 * read-port aggregation). These cases were migrated verbatim when the read
 * cluster moved behind the projection seam.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultAdminSubmissionProjection")
class AdminSubmissionProjectionTest {

    @Mock private SubmissionMapper submissionMapper;
    @Mock private AdminSubmissionReadPort submissionReadPort;
    @Mock private UserMapper userMapper;
    @Mock private ProblemMapper problemMapper;

    private DefaultAdminSubmissionProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultAdminSubmissionProjection(
                submissionMapper, submissionReadPort, userMapper, problemMapper,
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
            when(submissionMapper.findDistinctLanguages())
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
            // selectCount is called twice (last24h + pending); stub both.
            when(submissionMapper.selectCount(any())).thenReturn(50L);

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
}
