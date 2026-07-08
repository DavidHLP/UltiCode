package com.ulticode.modules.submission.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.dto.SubmissionListItemVO;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.CaseScope;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.stats.SubmissionStreakCalculator;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests the P0-1 user-projection contract of {@link DefaultSubmissionProjection}.
 *
 * <p>Focused on the new deep module: this test mocks only the four collaborators
 * the projection owns ({@code SubmissionMapper}, {@code UserMapper},
 * {@code ProblemMapper}, {@code ObjectMapper}). The previous incarnation
 * lived in {@code SubmissionVOProjectionTest} and required seventeen mocks
 * (every dependency of {@code SubmissionServiceImpl}) just to exercise the
 * projection rules.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultSubmissionProjection - P0-1 user projection")
class DefaultSubmissionProjectionTest {

    @Mock private SubmissionMapper submissionMapper;
    @Mock private SubmissionStreakCalculator submissionStreakCalculator;
    @Mock private UserMapper userMapper;
    @Mock private ProblemMapper problemMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DefaultSubmissionProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultSubmissionProjection(
                submissionMapper, submissionStreakCalculator, userMapper, problemMapper, objectMapper);
    }

    private Submission buildSubmission(Submission.TestCaseDetail... details) {
        Submission s = new Submission();
        s.setId("sub-1");
        s.setProblemId(100L);
        s.setUserId("u-1");
        s.setLanguage("java");
        s.setCode("class Solution { }");
        s.setStatus("Wrong Answer");
        s.setRuntime(100);
        s.setMemory(50.0);
        s.setCreatedAt(java.time.LocalDateTime.now());
        s.setTestDetails(Arrays.asList(details));
        return s;
    }

    private Submission.TestCaseDetail detail(String status, CaseScope scope, String caseId,
                                             String output, String expected, String error) {
        Submission.TestCaseDetail d = new Submission.TestCaseDetail();
        d.setStatus(status);
        d.setCaseScope(scope);
        d.setCaseId(caseId);
        d.setOutput(output);
        d.setExpectedOutput(expected);
        d.setDetail(error);
        d.setTime(50);
        d.setMemory(10.0);
        return d;
    }

    @Test
    @DisplayName("HIDDEN case is excluded from vo.tests; HIDDEN failure surfaces errorDetail but no I/O")
    void hiddenCaseExcludedFromTests() {
        Submission s = buildSubmission(
                detail("Accepted", CaseScope.SAMPLE, "tc-1", "out", "exp", null),
                detail("Wrong Answer", CaseScope.HIDDEN, "tc-h-1", "SECRET_OUT", "SECRET_EXP", "diff")
        );
        when(userMapper.selectById("u-1")).thenReturn(null);
        when(problemMapper.selectById(100L)).thenReturn(null);

        SubmissionVO vo = projection.toVO(s);

        // Only the sample appears in vo.tests; the hidden case is dropped entirely.
        assertThat(vo.getTests()).hasSize(1);
        assertThat(vo.getTests().get(0).getStatus()).isEqualTo("Accepted");
        // HIDDEN failure surfaces errorDetail (user knows something failed) but no I/O.
        assertThat(vo.getErrorDetail()).isEqualTo("diff");
        assertThat(vo.getOutput()).isNull();
        assertThat(vo.getExpectedOutput()).isNull();
        assertThat(vo.getInput()).isNull();
    }

    @Test
    @DisplayName("Legacy null-scope detail is treated as sample (user-visible)")
    void legacyNullScopeIsUserVisible() {
        Submission s = buildSubmission(
                detail("Wrong Answer", null, null, "out", "exp", "diff") // legacy
        );
        when(userMapper.selectById("u-1")).thenReturn(null);
        when(problemMapper.selectById(100L)).thenReturn(null);

        SubmissionVO vo = projection.toVO(s);

        assertThat(vo.getTests()).hasSize(1);
        assertThat(vo.getErrorDetail()).isEqualTo("diff");
        // Legacy sample failing — I/O SHOULD be exposed
        assertThat(vo.getOutput()).isEqualTo("out");
        assertThat(vo.getExpectedOutput()).isEqualTo("exp");
    }

    @Test
    @DisplayName("HIDDEN-only failure: errorDetail set, I/O NOT exposed")
    void hiddenOnlyFailureRedactsIO() {
        Submission s = buildSubmission(
                detail("Accepted", CaseScope.SAMPLE, "tc-1", "out", "exp", null),
                detail("Wrong Answer", CaseScope.HIDDEN, "tc-h-1", "SECRET_OUT", "SECRET_EXP", "diff")
        );
        when(userMapper.selectById("u-1")).thenReturn(null);
        when(problemMapper.selectById(100L)).thenReturn(null);

        SubmissionVO vo = projection.toVO(s);

        assertThat(vo.getTests()).hasSize(1); // only SAMPLE in tests
        // HIDDEN failed; sample passed → errorDetail from HIDDEN, no I/O
        assertThat(vo.getErrorDetail()).isEqualTo("diff");
        assertThat(vo.getOutput()).isNull();
        assertThat(vo.getExpectedOutput()).isNull();
        assertThat(vo.getInput()).isNull();
    }

    @Test
    @DisplayName("SAMPLE failure preferred over HIDDEN failure for first-failing detail")
    void sampleFailurePreferredOverHidden() {
        Submission s = buildSubmission(
                detail("Wrong Answer", CaseScope.HIDDEN, "tc-h-1", "HIDDEN_OUT", "HIDDEN_EXP", "hidden-diff"),
                detail("Wrong Answer", CaseScope.SAMPLE, "tc-1", "SAMPLE_OUT", "SAMPLE_EXP", "sample-diff")
        );
        when(userMapper.selectById("u-1")).thenReturn(null);
        when(problemMapper.selectById(100L)).thenReturn(null);

        SubmissionVO vo = projection.toVO(s);

        // SAMPLE's I/O wins over HIDDEN's
        assertThat(vo.getErrorDetail()).isEqualTo("sample-diff");
        assertThat(vo.getOutput()).isEqualTo("SAMPLE_OUT");
        assertThat(vo.getExpectedOutput()).isEqualTo("SAMPLE_EXP");
    }

    @Test
    @DisplayName("Compile Error on sample: vo.compilerError set, I/O also exposed")
    void compileErrorExposesBothCompilerAndIO() {
        Submission s = buildSubmission(
                detail("Compile Error", CaseScope.SAMPLE, "tc-1", "", "", "javac: class Solution is missing")
        );
        when(userMapper.selectById("u-1")).thenReturn(null);
        when(problemMapper.selectById(100L)).thenReturn(null);

        SubmissionVO vo = projection.toVO(s);

        assertThat(vo.getCompilerError()).isEqualTo("javac: class Solution is missing");
        assertThat(vo.getErrorDetail()).isEqualTo("javac: class Solution is missing");
        assertThat(vo.getOutput()).isEmpty();
        assertThat(vo.getExpectedOutput()).isEmpty();
    }

    @Nested
    @DisplayName("List projection")
    class ListProjection {

        @Test
        @DisplayName("User and problem info are populated from pre-loaded DTO (no extra mapper calls)")
        void populatesFromPreloadedDto() {
            SubmissionMapper.SubmissionWithProblem row =
                    new SubmissionMapper.SubmissionWithProblem(
                            "sub-9", 200L, "u-9", "java", "code", "Accepted",
                            42, 64.0, "notes", 0,
                            java.time.LocalDateTime.now(),
                            50.0, 60.0, null, null, null,
                            "Two Sum", "two-sum");

            SubmissionListItemVO vo = projection.toListItemVO(row);

            assertThat(vo.getId()).isEqualTo("sub-9");
            assertThat(vo.getStatus()).isEqualTo("Accepted");
            assertThat(vo.getLanguage()).isEqualTo("java");
            assertThat(vo.getRuntime()).isEqualTo(42);
            assertThat(vo.getMemory()).isEqualTo(64.0);
            assertThat(vo.getProblem()).isNotNull();
            assertThat(vo.getProblem().getId()).isEqualTo(200L);
            assertThat(vo.getProblem().getTitle()).isEqualTo("Two Sum");
            assertThat(vo.getProblem().getSlug()).isEqualTo("two-sum");
        }

        @Test
        @DisplayName("Null problem fields on the DTO do not break the projection")
        void nullProblemFieldsDoNotBreakProjection() {
            SubmissionMapper.SubmissionWithProblem row =
                    new SubmissionMapper.SubmissionWithProblem(
                            "sub-9", 200L, "u-9", "java", "code", "Accepted",
                            42, 64.0, null, 0,
                            java.time.LocalDateTime.now(),
                            null, null, null, null, null,
                            null, null);

            SubmissionListItemVO vo = projection.toListItemVO(row);

            assertThat(vo.getId()).isEqualTo("sub-9");
            assertThat(vo.getProblem()).isNull();
        }
    }

    @Nested
    @DisplayName("Aggregate delegation")
    class AggregateDelegation {

        @Test
        @DisplayName("aggregateDates forwards to the mapper unchanged")
        void aggregateDatesDelegates() {
            when(submissionMapper.findSubmissionDatesByYear("u-1", 2026))
                    .thenReturn(List.of("2026-01-01", "2026-01-02"));

            List<String> result = projection.aggregateDates("u-1", 2026);

            assertThat(result).containsExactly("2026-01-01", "2026-01-02");
        }

        @Test
        @DisplayName("aggregateDates passes through a null year")
        void aggregateDatesPassesNullYear() {
            when(submissionMapper.findSubmissionDatesByYear("u-1", null))
                    .thenReturn(List.of("2025-12-31"));

            assertThat(projection.aggregateDates("u-1", null))
                    .containsExactly("2025-12-31");
        }

        @Test
        @DisplayName("Empty learning progress: empty weekly, totals zero")
        void aggregateLearningProgressEmpty() {
            when(submissionMapper.findWeeklyProgress("u-1")).thenReturn(List.of());
            when(submissionStreakCalculator.computeStreak("u-1")).thenReturn(0);

            var result = projection.aggregateLearningProgress("u-1");

            assertThat(result.getWeeklyProgress()).isEmpty();
            assertThat(result.getTotalProblems()).isZero();
            assertThat(result.getTotalTimeHours()).isZero();
            assertThat(result.getAvgTimePerProblem()).isZero();
            assertThat(result.getCurrentStreak()).isZero();
            assertThat(result.getLongestStreak()).isZero();
        }

        @Test
        @DisplayName("Empty history: monthly and language lists empty, acceptance 0.0")
        void aggregateHistoryEmpty() {
            when(submissionMapper.findMonthlySubmissionStats("u-1")).thenReturn(List.of());
            when(submissionMapper.findLanguageStats("u-1")).thenReturn(List.of());

            var result = projection.aggregateHistory("u-1");

            assertThat(result.getMonthly()).isEmpty();
            assertThat(result.getLanguages()).isEmpty();
            assertThat(result.getTotalSubmissions()).isZero();
            assertThat(result.getTotalAccepted()).isZero();
            assertThat(result.getAcceptanceRate()).isZero();
        }
    }
}
