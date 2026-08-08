package com.ulticode.modules.submission.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.dto.CreateSubmissionDTO;
import com.ulticode.app.api.dto.SubmissionDetailVO;
import com.ulticode.app.api.dto.SubmissionListItemVO;
import com.ulticode.app.api.dto.SubmissionQueryDTO;
import com.ulticode.app.api.dto.SubmissionVO;
import com.ulticode.app.api.dto.UserBestStats;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.domain.submission.enums.SubmissionStatus;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.submission.mapper.SubmissionMapper.SubmissionWithProblem;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.app.api.service.JudgeEnqueuePort;
import com.ulticode.app.api.service.ContestSubmissionPort;
import com.ulticode.app.api.service.UserExistencePort;
import com.ulticode.app.api.service.AchievementTriggerPort;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.submission.dispatcher.JudgedNotificationDispatcher;
import com.ulticode.modules.submission.outbox.mapper.JudgeOutboxMapper;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SubmissionServiceImpl")
class SubmissionServiceImplTest {

    @Mock private SubmissionMapper submissionMapper;
    @Mock private UserExistencePort userExistencePort;
    @Mock private ProblemFactsPort problemFacts;
    @Mock private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock private Clock clock;
    @Mock private JudgeEnqueuePort judgeEnqueuePort;
    @Mock private ContestSubmissionPort contestSubmissionPort;
    @Mock private AchievementTriggerPort achievementTriggerPort;
    @Mock private JudgedNotificationDispatcher judgedNotificationDispatcher;
    @Mock private SubmissionProjection submissionProjection;
    @Mock private JudgeOutboxMapper judgeOutboxMapper;
    @Mock private ApplicationEventPublisher applicationEventPublisher;
    @Mock private UuidGenerator uuidGenerator;

    private SubmissionServiceImpl submissionService;
    private com.ulticode.modules.submission.port.DefaultSubmissionWritePort writePort;

    private static final String USER_ID = "user-123";
    private static final Long PROBLEM_ID = 1L;
    private static final String LANGUAGE = "java";
    private static final String CODE = "public class Main {}";

    @BeforeEach
    void setUp() {
        com.ulticode.modules.submission.config.FeatureFlagsProperties flags =
                new com.ulticode.modules.submission.config.FeatureFlagsProperties();
        lenient().when(clock.instant()).thenReturn(java.time.Instant.now());
        lenient().when(clock.getZone()).thenReturn(java.time.ZoneId.systemDefault());
        com.ulticode.modules.submission.stats.DefaultSubmissionPerformanceStats performanceStats =
                new com.ulticode.modules.submission.stats.DefaultSubmissionPerformanceStats(submissionMapper);
        writePort =
                new com.ulticode.modules.submission.port.DefaultSubmissionWritePort(
                        submissionMapper, problemFacts, userExistencePort, objectMapper,
                        submissionProjection, performanceStats,
                        judgeEnqueuePort, contestSubmissionPort,
                        achievementTriggerPort,
                        judgedNotificationDispatcher,
                        judgeOutboxMapper, flags, null, applicationEventPublisher, clock,
                        uuidGenerator);
        submissionService = new SubmissionServiceImpl(
                submissionMapper, submissionProjection, performanceStats, writePort);
        lenient().when(submissionProjection.toVO(any(com.ulticode.modules.submission.entity.Submission.class)))
                .thenAnswer(inv -> {
                    com.ulticode.modules.submission.entity.Submission s = inv.getArgument(0);
                    com.ulticode.app.api.dto.SubmissionVO vo = new com.ulticode.app.api.dto.SubmissionVO();
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
                    com.ulticode.app.api.dto.PerformanceStats stats = inv.getArgument(1);
                    com.ulticode.app.api.dto.SubmissionDetailVO vo = new com.ulticode.app.api.dto.SubmissionDetailVO();
                    vo.setId(s.getId());
                    vo.setProblemId(s.getProblemId());
                    vo.setUserId(s.getUserId());
                    vo.setLanguage(s.getLanguage());
                    vo.setCode(s.getCode());
                    vo.setStatus(s.getStatus());
                    vo.setRuntime(s.getRuntime());
                    vo.setMemory(s.getMemory());
                    vo.setCreatedAt(s.getCreatedAt());
                    if (stats != null) {
                        vo.setRuntimePercentile(stats.runtimePercentile());
                        vo.setMemoryPercentile(stats.memoryPercentile());
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
                    com.ulticode.app.api.dto.SubmissionListItemVO vo = new com.ulticode.app.api.dto.SubmissionListItemVO();
                    vo.setId(s.id());
                    vo.setStatus(s.status());
                    vo.setLanguage(s.language());
                    vo.setRuntime(s.runtime());
                    vo.setMemory(s.memory());
                    vo.setCreatedAt(s.createdAt());
                    vo.setNotes(s.notes());
                    if (s.problemTitle() != null) {
                        com.ulticode.app.api.dto.SubmissionListItemVO.ProblemSummary problemSummary =
                                new com.ulticode.app.api.dto.SubmissionListItemVO.ProblemSummary();
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
            when(userExistencePort.existsById(USER_ID)).thenReturn(true);
            when(submissionMapper.insert(any(Submission.class))).thenReturn(1);

            SubmissionVO result = writePort.submit(USER_ID, createDTO());

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo("Pending");
            assertThat(result.getLanguage()).isEqualTo(LANGUAGE);
            assertThat(result.getUserId()).isEqualTo(USER_ID);
            assertThat(result.getProblemId()).isEqualTo(PROBLEM_ID);
            verify(submissionMapper).insert(any(Submission.class));
        }

        @Test
        @DisplayName("null userId throws BAD_REQUEST")
        void submit_nullUserId_throwsException() {
            assertThatThrownBy(() -> writePort.submit(null, createDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(BaseErrorCode.BAD_REQUEST));
        }

        @Test
        @DisplayName("empty code throws BAD_REQUEST")
        void submit_emptyCode_throwsException() {
            CreateSubmissionDTO dto = createDTO();
            dto.setCode("");

            assertThatThrownBy(() -> writePort.submit(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(BaseErrorCode.BAD_REQUEST));
        }

        @Test
        @DisplayName("unsupported language throws BAD_REQUEST")
        void submit_unsupportedLanguage_throwsException() {
            CreateSubmissionDTO dto = createDTO();
            dto.setLanguage("assembly");

            assertThatThrownBy(() -> writePort.submit(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(BaseErrorCode.BAD_REQUEST));
        }

        @Test
        @DisplayName("problem not found throws NOT_FOUND")
        void submit_problemNotFound_throwsException() {
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(null);

            assertThatThrownBy(() -> writePort.submit(USER_ID, createDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(BaseErrorCode.NOT_FOUND));
        }

        @Test
        @DisplayName("user not found throws NOT_FOUND")
        void submit_userNotFound_throwsException() {
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());
            when(userExistencePort.existsById(USER_ID)).thenReturn(false);

            assertThatThrownBy(() -> writePort.submit(USER_ID, createDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(BaseErrorCode.NOT_FOUND));
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
        @DisplayName("existing submission returns SubmissionDetailVO")
        void findById_existingSubmission_returnsVO() {
            Submission submission = createValidSubmission();

            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
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
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            SubmissionDetailVO result = submissionService.findById("sub-123", USER_ID);

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
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            SubmissionDetailVO result = submissionService.findById("sub-123", USER_ID);

            assertThat(result.getMemoryDistBinsMb()).isNotNull();
            assertThat((List<?>) result.getMemoryDistBinsMb()).isNotEmpty();
        }

        @Test
        @DisplayName("non-Accepted submission skips performance stats computation")
        void findById_nonAccepted_skipsPerfStats() {
            Submission submission = createValidSubmission();
            submission.setStatus("Wrong Answer");

            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            SubmissionDetailVO result = submissionService.findById("sub-123", USER_ID);

            verify(submissionMapper, never()).findBestStatsByProblemAndLanguage(any(), any());
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("not found throws NOT_FOUND")
        void findById_notFound_throwsException() {
            when(submissionMapper.selectById("nonexistent")).thenReturn(null);

            assertThatThrownBy(() -> submissionService.findById("nonexistent", USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(BaseErrorCode.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("updateSubmissionResult()")
    class UpdateSubmissionResultTest {

        @Test
        @DisplayName("accepted result stores percentiles and visible distribution bins")
        void updateSubmissionResult_Accepted_updatesPerformanceDistribution() {
            Submission submission = createValidSubmission();
            submission.setStatus("Pending");

            List<UserBestStats> peerBests = List.of(
                    new UserBestStats("user-fast", 80, 192.0),
                    new UserBestStats("user-slow", 120, 320.0));

            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(submissionMapper.findBestStatsByProblemAndLanguage(PROBLEM_ID, LANGUAGE))
                    .thenReturn(peerBests);
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            writePort.updateSubmissionResult("sub-123", SubmissionStatus.ACCEPTED, 100, 256.0, null);

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

            List<UserBestStats> peerBests = List.of(
                    new UserBestStats("other-user", 50, 128.0));

            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(submissionMapper.findBestStatsByProblemAndLanguage(PROBLEM_ID, LANGUAGE))
                    .thenReturn(peerBests);
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            writePort.updateSubmissionResult("sub-123", SubmissionStatus.ACCEPTED, 100, 256.0, null);

            verify(submissionMapper).updateById(argThat((Submission updated) -> {
                assertThat(updated.getRuntimePercentile()).isEqualTo(0.0);
                assertThat(updated.getMemoryPercentile()).isEqualTo(0.0);
                assertThat((List<?>) updated.getRuntimeDistBinsMs()).hasSize(2);
                assertThat((List<?>) updated.getMemoryDistBinsMb()).hasSize(2);
                return true;
            }));
        }

        @Test
        @DisplayName("wrong answer result updates without exception")
        void updateSubmissionResult_WrongAnswer_noException() {
            Submission submission = createValidSubmission();
            submission.setStatus("Pending");
            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            writePort.updateSubmissionResult("sub-123", SubmissionStatus.WRONG_ANSWER, 50, 128.0, null);

            verify(submissionMapper).updateById(submission);
        }

        @Test
        @DisplayName("notification dispatcher is invoked on accepted verdict")
        void updateSubmissionResult_Accepted_invokesDispatcher() {
            Submission submission = createValidSubmission();
            submission.setStatus("Pending");
            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(problemFacts.findDisplayFacts(PROBLEM_ID)).thenReturn(createValidProblem());

            writePort.updateSubmissionResult("sub-123", SubmissionStatus.ACCEPTED, 100, 256.0, null);

            verify(judgedNotificationDispatcher).dispatch(any(), eq(SubmissionStatus.ACCEPTED), anyLong(), any());
            verify(submissionMapper).updateById(submission);
        }
    }
}
