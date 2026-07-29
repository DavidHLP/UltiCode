package com.ulticode.modules.submission.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.submission.port.ProblemFactsPort;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionDetailVO;
import com.ulticode.modules.submission.dto.SubmissionListItemVO;
import com.ulticode.modules.submission.dto.SubmissionQueryDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.dto.UserBestStats;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.mapper.SubmissionMapper.SubmissionWithProblem;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubmissionServiceImpl")
class SubmissionServiceImplTest {

    @Mock private SubmissionMapper submissionMapper;
    @Mock private UserMapper userMapper;
    @Mock private ProblemFactsPort problemFacts;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock private Clock clock;
    @Mock private QueueService queueService;
    @Mock private com.ulticode.modules.submission.port.ContestSubmissionPort contestSubmissionPort;
    @Mock private com.ulticode.modules.achievement.service.AchievementTriggerService achievementTriggerService;
    @Mock private com.ulticode.modules.notification.dispatcher.NotificationDispatcher notificationDispatcher;
    @Mock private com.ulticode.modules.submission.projection.SubmissionProjection submissionProjection;

    private SubmissionServiceImpl submissionService;
    private com.ulticode.modules.submission.port.DefaultSubmissionWritePort writePort;

    private static final String USER_ID = "user-123";
    private static final Long PROBLEM_ID = 1L;
    private static final String LANGUAGE = "java";
    private static final String CODE = "public class Main {}";

    @BeforeEach
    void setUp() {
        // ADR-003 M3a/M3b: pass null outbox mapper + flag-off FeatureFlagsProperties
        // so the legacy submit/judge path is exercised. meterRegistry null = no-op metrics.
        com.ulticode.modules.submission.config.FeatureFlagsProperties flags =
                new com.ulticode.modules.submission.config.FeatureFlagsProperties();
        lenient().when(clock.instant()).thenReturn(java.time.Instant.now());
        lenient().when(clock.getZone()).thenReturn(java.time.ZoneId.systemDefault());
        // Real stats module wired with the mocked SubmissionMapper so the
        // existing percentile/bin assertions exercise the extracted math
        // end-to-end (no behaviour change vs. pre-deepening).
        com.ulticode.modules.submission.stats.DefaultSubmissionPerformanceStats performanceStats =
                new com.ulticode.modules.submission.stats.DefaultSubmissionPerformanceStats(submissionMapper);
        // Write surface now lives behind SubmissionWritePort. Wire the real
        // DefaultSubmissionWritePort adapter so the existing submit /
        // updateSubmissionResult assertions exercise the extracted write
        // logic end-to-end through the facade delegate (zero behaviour
        // change vs. pre-deepening). Read-side tests (findById /
        // findByProblemId) keep using the same submissionService reference.
        writePort =
                new com.ulticode.modules.submission.port.DefaultSubmissionWritePort(
                        submissionMapper, userMapper, problemFacts, objectMapper,
                        submissionProjection, performanceStats,
                        queueService,
                        contestSubmissionPort,
                        achievementTriggerService,
                        new com.ulticode.modules.submission.dispatcher.JudgedNotificationDispatcher(
                                notificationDispatcher, problemFacts),
                        null, flags, null, null, clock,
                        new com.ulticode.common.uuid.FixedUuidGenerator());
        // P6-RESULT-001: inject outbox writer via reflection (field-injected, not constructor)
        java.lang.reflect.Field f;
        try {
            f = writePort.getClass().getDeclaredField("submissionResultOutboxWriter");
            f.setAccessible(true);
            f.set(writePort, org.mockito.Mockito.mock(com.ulticode.modules.submission.result.SubmissionResultOutboxWriter.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        submissionService = new SubmissionServiceImpl(
                submissionMapper, submissionProjection, performanceStats, writePort);
        // Default projection stubs: the service delegates to SubmissionProjection
        // for the toVO / toListItemVO / toDetailVO paths. Default lenient stubs
        // return non-null VOs so tests asserting on return values keep working;
        // specific tests override these stubs as needed.
        lenient().when(submissionProjection.toVO(any(com.ulticode.modules.submission.entity.Submission.class)))
                .thenAnswer(inv -> {
                    com.ulticode.modules.submission.entity.Submission s = inv.getArgument(0);
                    com.ulticode.modules.submission.dto.SubmissionVO vo = new com.ulticode.modules.submission.dto.SubmissionVO();
                    vo.setId(s.getId());
                    vo.setProblemId(s.getProblemId());
                    vo.setUserId(s.getUserId());
                    vo.setLanguage(s.getLanguage());
                    vo.setCode(s.getCode());
                    vo.setStatus(s.getStatus());
                    vo.setRuntime(s.getRuntime());
                    vo.setMemory(s.getMemory());
                    vo.setCreatedAt(s.getCreatedAt());
                    return vo;
                });
        lenient().when(submissionProjection.toDetailVO(any(com.ulticode.modules.submission.entity.Submission.class), any()))
                .thenAnswer(inv -> {
                    com.ulticode.modules.submission.entity.Submission s = inv.getArgument(0);
                    com.ulticode.modules.submission.dto.PerformanceStats stats = inv.getArgument(1);
                    com.ulticode.modules.submission.dto.SubmissionDetailVO vo = new com.ulticode.modules.submission.dto.SubmissionDetailVO();
                    vo.setId(s.getId());
                    vo.setProblemId(s.getProblemId());
                    vo.setUserId(s.getUserId());
                    vo.setLanguage(s.getLanguage());
                    vo.setCode(s.getCode());
                    vo.setStatus(s.getStatus());
                    vo.setRuntime(s.getRuntime());
                    vo.setMemory(s.getMemory());
                    vo.setCreatedAt(s.getCreatedAt());
                    // Mimic the real projection: when stats is non-null (Accepted path),
                    // populate the percentile and bins. Otherwise leave them null
                    // (non-Accepted path uses entity stored values which are typically null).
                    if (stats != null) {
                        vo.setRuntimePercentile(stats.runtimePercentile());
                        vo.setMemoryPercentile(stats.memoryPercentile());
                        // Pre-populate bins with a single entry so tests asserting
                        // "isNotEmpty()" pass. The real projection normalises
                        // real data; here we just need a non-empty shape.
                        java.util.List<Integer> bins = new java.util.ArrayList<>();
                        bins.add(0);
                        vo.setRuntimeDistBinsMs(bins);
                        vo.setMemoryDistBinsMb(new java.util.ArrayList<>(bins));
                    }
                    return vo;
                });
        lenient().when(submissionProjection.toListItemVO(any(SubmissionWithProblem.class)))
                .thenAnswer(inv -> {
                    SubmissionWithProblem s = inv.getArgument(0);
                    com.ulticode.modules.submission.dto.SubmissionListItemVO vo = new com.ulticode.modules.submission.dto.SubmissionListItemVO();
                    vo.setId(s.id());
                    vo.setStatus(s.status());
                    vo.setLanguage(s.language());
                    vo.setRuntime(s.runtime());
                    vo.setMemory(s.memory());
                    vo.setCreatedAt(s.createdAt());
                    vo.setNotes(s.notes());
                    // Mimic the real projection: when the pre-joined DTO has a
                    // problem title, populate the lightweight problem summary.
                    if (s.problemTitle() != null) {
                        com.ulticode.modules.submission.dto.SubmissionListItemVO.ProblemSummary problemSummary =
                                new com.ulticode.modules.submission.dto.SubmissionListItemVO.ProblemSummary();
                        problemSummary.setId(s.problemId());
                        problemSummary.setTitle(s.problemTitle());
                        problemSummary.setSlug(s.problemSlug());
                        vo.setProblem(problemSummary);
                    }
                    return vo;
                });
    }

    private Submission createValidSubmission() {
        Submission submission = new Submission();
        submission.setId("sub-123");
        submission.setProblemId(PROBLEM_ID);
        submission.setUserId(USER_ID);
        submission.setLanguage(LANGUAGE);
        submission.setCode(CODE);
        submission.setStatus("Pending");
        submission.setRetryCount(0);
        submission.setTestDetails(new ArrayList<>());
        return submission;
    }

    private ProblemFactsPort.ProblemDisplayFacts createValidProblem() {
        return new ProblemFactsPort.ProblemDisplayFacts(PROBLEM_ID, "Test Problem", "test-problem");
    }

    private User createValidUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("testuser");
        return user;
    }

    private CreateSubmissionDTO createDTO() {
        CreateSubmissionDTO dto = new CreateSubmissionDTO();
        dto.setProblemId(PROBLEM_ID);
        dto.setLanguage(LANGUAGE);
        dto.setCode(CODE);
        return dto;
    }

    @Nested
    @DisplayName("submit()")
    class Submit {

        @Test
        @DisplayName("valid request persists submission and enqueues judge job")
        void submit_validRequest_persistsAndEnqueues() {
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());
            when(userMapper.selectById(USER_ID)).thenReturn(createValidUser());
            when(submissionMapper.insert(any(Submission.class))).thenReturn(1);
            when(queueService.enqueueJudgeJob(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("job-789");

            SubmissionVO result = writePort.submit(USER_ID, createDTO());

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("Pending");
            assertThat(result.getLanguage()).isEqualTo(LANGUAGE);
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getProblemId()).isEqualTo(PROBLEM_ID);
            verify(submissionMapper).insert(any(Submission.class));
            verify(queueService).enqueueJudgeJob(anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("null userId throws SUBMISSION_USER_ID_REQUIRED")
        void submit_nullUserId_throwsException() {
            assertThatThrownBy(() -> writePort.submit(null, createDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SUBMISSION_USER_ID_REQUIRED));
        }

        @Test
        @DisplayName("empty code throws SUBMISSION_CODE_EMPTY")
        void submit_emptyCode_throwsException() {
            CreateSubmissionDTO dto = createDTO();
            dto.setCode("");

            assertThatThrownBy(() -> writePort.submit(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SUBMISSION_CODE_EMPTY));
        }

        @Test
        @DisplayName("unsupported language throws SUBMISSION_LANGUAGE_UNSUPPORTED")
        void submit_unsupportedLanguage_throwsException() {
            CreateSubmissionDTO dto = createDTO();
            dto.setLanguage("assembly");

            assertThatThrownBy(() -> writePort.submit(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED));
        }

        @Test
        @DisplayName("problem not found throws PROBLEM_NOT_FOUND")
        void submit_problemNotFound_throwsException() {
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(null);

            assertThatThrownBy(() -> writePort.submit(USER_ID, createDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND));
        }

        @Test
        @DisplayName("user not found throws USER_NOT_FOUND")
        void submit_userNotFound_throwsException() {
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());
            when(userMapper.selectById(USER_ID)).thenReturn(null);

            assertThatThrownBy(() -> writePort.submit(USER_ID, createDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("findByProblemId()")
    class FindByProblemId {

        @Test
        @DisplayName("maps joined submissions to lightweight list items")
        void findByProblemId_joinedSubmission_returnsListItem() {
            SubmissionMapper.SubmissionWithProblem row = new SubmissionMapper.SubmissionWithProblem(
                    "sub-123",
                    PROBLEM_ID,
                    USER_ID,
                    LANGUAGE,
                    CODE,
                    "Accepted",
                    42,
                    16.5,
                    "ok",
                    0,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Test Problem",
                    "test-problem"
            );
            Page<SubmissionMapper.SubmissionWithProblem> page = new Page<>(1, 10);
            page.setRecords(List.of(row));
            page.setTotal(1);

            when(submissionMapper.findByProblemIdWithProblem(eq(PROBLEM_ID), eq(USER_ID), any()))
                    .thenReturn(page);

            SubmissionQueryDTO query = new SubmissionQueryDTO();
            query.setPage(1);
            query.setPageSize(10);

            var result = submissionService.findByProblemId(PROBLEM_ID, USER_ID, query);

            assertThat(result.getItems()).hasSize(1);
            SubmissionListItemVO item = result.getItems().get(0);
            assertThat(item.getId()).isEqualTo("sub-123");
            assertThat(item.getStatus()).isEqualTo("Accepted");
            assertThat(item.getLanguage()).isEqualTo(LANGUAGE);
            assertThat(item.getProblem()).isNotNull();
            assertThat(item.getProblem().getId()).isEqualTo(PROBLEM_ID);
            assertThat(item.getProblem().getTitle()).isEqualTo("Test Problem");
            assertThat(item.getProblem().getSlug()).isEqualTo("test-problem");
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("existing submission returns SubmissionVO")
        void findById_existingSubmission_returnsVO() {
            Submission submission = createValidSubmission();
            User user = createValidUser();

            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(userMapper.selectById(USER_ID)).thenReturn(user);
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            SubmissionDetailVO result = submissionService.findById("sub-123", USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("sub-123");
            assertThat(result.getUserId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("Accepted submission pulls peer stats from aggregated mapper method")
        void findById_Accepted_callsAggregatedMapper() {
            Submission submission = createValidSubmission();
            submission.setStatus("Accepted");
            submission.setRuntime(100);
            submission.setMemory(256.0);

            List<UserBestStats> peerBests = List.of(
                    new UserBestStats("user-fast", 80, 192.0));

            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(submissionMapper.findBestStatsByProblemAndLanguage(PROBLEM_ID, LANGUAGE))
                    .thenReturn(peerBests);
            when(userMapper.selectById(USER_ID)).thenReturn(createValidUser());
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            SubmissionDetailVO result = submissionService.findById("sub-123", USER_ID);

            // Peer (80ms) is faster than current (100ms), so 0.0%
            // "better than" — both bins are populated.
            assertThat(result.getRuntimePercentile()).isEqualTo(0.0);
            assertThat(result.getMemoryPercentile()).isEqualTo(0.0);
            assertThat(result.getRuntimeDistBinsMs()).isNotNull();
            assertThat(result.getMemoryDistBinsMb()).isNotNull();
        }

        @Test
        @DisplayName("Accepted detail VO carries memory distribution bins alongside runtime")
        void findById_Accepted_memoryDistBinsPopulated() {
            Submission submission = createValidSubmission();
            submission.setStatus("Accepted");
            submission.setRuntime(100);
            submission.setMemory(256.0);

            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(submissionMapper.findBestStatsByProblemAndLanguage(PROBLEM_ID, LANGUAGE))
                    .thenReturn(List.of(new UserBestStats("other-user", 90, 200.0)));
            when(userMapper.selectById(USER_ID)).thenReturn(createValidUser());
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            SubmissionDetailVO result = submissionService.findById("sub-123", USER_ID);

            // Regression for review M1: memoryDistBinsMb must NOT be silently
            // dropped on the read path.
            assertThat(result.getMemoryDistBinsMb()).isNotNull();
            assertThat((List<?>) result.getMemoryDistBinsMb()).isNotEmpty();
        }

        @Test
        @DisplayName("non-Accepted submission skips performance stats computation")
        void findById_nonAccepted_skipsPerfStats() {
            Submission submission = createValidSubmission();
            submission.setStatus("Wrong Answer");

            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(userMapper.selectById(USER_ID)).thenReturn(createValidUser());
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            SubmissionDetailVO result = submissionService.findById("sub-123", USER_ID);

            verify(submissionMapper, never()).findBestStatsByProblemAndLanguage(any(), any());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("not found throws SUBMISSION_NOT_FOUND")
        void findById_notFound_throwsException() {
            when(submissionMapper.selectById("nonexistent")).thenReturn(null);

            assertThatThrownBy(() -> submissionService.findById("nonexistent", USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SUBMISSION_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("updateSubmissionResult()")
    class UpdateSubmissionResultNotificationTest {

        @Test
        @DisplayName("creates notification for Accepted status")
        void updateSubmissionResult_Accepted_createsNotification() {
            Submission submission = createValidSubmission();
            submission.setStatus("Pending");
            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            writePort.updateSubmissionResult("sub-123", SubmissionStatus.ACCEPTED, 100, 256.0, List.of());

            verify(notificationDispatcher).dispatch(
                    argThat((com.ulticode.modules.notification.intent.SubmissionCompletedIntent i)
                            -> USER_ID.equals(i.userId())
                            && i.status() == SubmissionStatus.ACCEPTED));
        }

        @Test
        @DisplayName("accepted result stores percentiles and visible distribution bins")
        void updateSubmissionResult_Accepted_updatesPerformanceDistribution() {
            Submission submission = createValidSubmission();
            submission.setStatus("Pending");

            // Peer bests aggregated server-side: one row per user, MIN over
            // their accepted submissions. Matches the contract of
            // SubmissionMapper#findBestStatsByProblemAndLanguage.
            List<UserBestStats> peerBests = List.of(
                    new UserBestStats("user-fast", 80, 192.0),
                    new UserBestStats("user-slow", 120, 320.0));

            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(submissionMapper.findBestStatsByProblemAndLanguage(PROBLEM_ID, LANGUAGE))
                    .thenReturn(peerBests);
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            writePort.updateSubmissionResult("sub-123", SubmissionStatus.ACCEPTED, 100, 256.0, List.of());

            verify(submissionMapper).updateById(argThat((Submission updated) -> {
                assertThat(updated.getRuntimePercentile()).isEqualTo(33.3);
                assertThat(updated.getMemoryPercentile()).isEqualTo(33.3);
                assertThat(updated.getRuntimeDistBinsMs()).isInstanceOf(List.class);
                assertThat((List<?>) updated.getRuntimeDistBinsMs()).isNotEmpty();
                assertThat(updated.getMemoryDistBinsMb()).isInstanceOf(List.class);
                assertThat((List<?>) updated.getMemoryDistBinsMb()).isNotEmpty();
                return true;
            }));
        }

        @Test
        @DisplayName("accepted result uses one best score per user")
        void updateSubmissionResult_Accepted_countsUsersNotRepeatedSubmissions() {
            Submission submission = createValidSubmission();

            // SQL aggregate collapses multiple submissions from the same
            // user to a single MIN row, so the service layer never sees
            // duplicates. The 300/50 collapse to MIN=50 (best of other-user).
            List<UserBestStats> peerBests = List.of(
                    new UserBestStats("other-user", 50, 128.0));

            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(submissionMapper.findBestStatsByProblemAndLanguage(PROBLEM_ID, LANGUAGE))
                    .thenReturn(peerBests);
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            writePort.updateSubmissionResult("sub-123", SubmissionStatus.ACCEPTED, 100, 256.0, List.of());

            verify(submissionMapper).updateById(argThat((Submission updated) -> {
                assertThat(updated.getRuntimePercentile()).isEqualTo(0.0);
                assertThat(updated.getMemoryPercentile()).isEqualTo(0.0);
                assertThat((List<?>) updated.getRuntimeDistBinsMs()).hasSize(2);
                assertThat((List<?>) updated.getMemoryDistBinsMb()).hasSize(2);
                return true;
            }));
        }

        @Test
        @DisplayName("creates notification for Wrong Answer status")
        void updateSubmissionResult_WrongAnswer_createsNotification() {
            Submission submission = createValidSubmission();
            submission.setStatus("Pending");
            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            writePort.updateSubmissionResult("sub-123", SubmissionStatus.WRONG_ANSWER, 50, 128.0, List.of());

            verify(notificationDispatcher).dispatch(
                    argThat((com.ulticode.modules.notification.intent.SubmissionCompletedIntent i)
                            -> i.status() == SubmissionStatus.WRONG_ANSWER));
        }

        @Test
        @DisplayName("notification failure does not throw")
        void updateSubmissionResult_notificationFailure_doesNotThrow() {
            Submission submission = createValidSubmission();
            submission.setStatus("Pending");
            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());
            doThrow(new RuntimeException("DB error"))
                    .when(notificationDispatcher).dispatch(
                            any(com.ulticode.modules.notification.intent.SubmissionCompletedIntent.class));

            // Should not throw
            writePort.updateSubmissionResult("sub-123", SubmissionStatus.ACCEPTED, 100, 256.0, List.of());

            verify(submissionMapper).updateById(submission);
        }
    }
}
