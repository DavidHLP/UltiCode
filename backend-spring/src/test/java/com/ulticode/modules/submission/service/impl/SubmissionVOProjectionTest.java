package com.ulticode.modules.submission.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.config.FeatureFlagsProperties;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.notification.dispatcher.NotificationDispatcher;
import com.ulticode.modules.notification.service.NotificationDispatchService;
import com.ulticode.modules.notification.service.NotificationService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.queue.outbox.mapper.JudgeOutboxMapper;
import com.ulticode.modules.queue.service.QueueService;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.enums.CaseScope;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.websocket.service.RealtimeService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * P0-1: {@code SubmissionServiceImpl.toVO(Submission)} must project
 * per-case details through {@code CaseScope.isUserVisible()} so that
 * the canonical user-facing {@code SubmissionVO} never carries hidden
 * case inputs / outputs / expectedOutput.
 *
 * <p>This is a pure unit test: all collaborators are stubbed so we can
 * construct scenarios covering SAMPLE, HIDDEN, legacy null, and mixed
 * failing-detail ordering.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionServiceImpl.toVO - P0-1 user projection")
class SubmissionVOProjectionTest {

    @Mock private SubmissionMapper submissionMapper;
    @Mock private UserMapper userMapper;
    @Mock private ProblemMapper problemMapper;
    @Mock private QueueService queueService;
    @Mock private RealtimeService realtimeService;
    @Mock private ContestProblemMapper contestProblemMapper;
    @Mock private ContestSubmissionMapper contestSubmissionMapper;
    @Mock private ContestMapper contestMapper;
    @Mock private ContestParticipantMapper contestParticipantMapper;
    @Mock private AchievementTriggerService achievementTriggerService;
    @Mock private NotificationService notificationService;
    @Mock private NotificationDispatchService notificationDispatchService;
    @Mock private NotificationDispatcher notificationDispatcher;
    @Mock private JudgeOutboxMapper judgeOutboxMapper;
    @Mock private FeatureFlagsProperties featureFlags;
    @Mock private MeterRegistry meterRegistry;

    private SubmissionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SubmissionServiceImpl(
                submissionMapper, userMapper, problemMapper, new ObjectMapper(),
                queueService, realtimeService, contestProblemMapper, contestSubmissionMapper,
                contestMapper, contestParticipantMapper, achievementTriggerService,
                notificationService, notificationDispatchService, notificationDispatcher,
                judgeOutboxMapper, featureFlags, meterRegistry, null);
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

        SubmissionVO vo = service.toVO(s);

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

        SubmissionVO vo = service.toVO(s);

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

        SubmissionVO vo = service.toVO(s);

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

        SubmissionVO vo = service.toVO(s);

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

        SubmissionVO vo = service.toVO(s);

        assertThat(vo.getCompilerError()).isEqualTo("javac: class Solution is missing");
        assertThat(vo.getErrorDetail()).isEqualTo("javac: class Solution is missing");
        assertThat(vo.getOutput()).isEmpty();
        assertThat(vo.getExpectedOutput()).isEmpty();
    }
}
