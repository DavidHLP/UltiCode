package com.ulticode.modules.submission.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
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

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubmissionServiceImpl")
class SubmissionServiceImplTest {

    @Mock private SubmissionMapper submissionMapper;
    @Mock private UserMapper userMapper;
    @Mock private ProblemMapper problemMapper;
    @Mock private QueueService queueService;
    @Mock private com.ulticode.modules.websocket.service.RealtimeService realtimeService;
    @Mock private com.ulticode.modules.contest.mapper.ContestProblemMapper contestProblemMapper;
    @Mock private com.ulticode.modules.contest.mapper.ContestSubmissionMapper contestSubmissionMapper;
    @Mock private com.ulticode.modules.contest.mapper.ContestMapper contestMapper;
    @Mock private com.ulticode.modules.contest.mapper.ContestParticipantMapper contestParticipantMapper;

    private SubmissionServiceImpl submissionService;

    private static final String USER_ID = "user-123";
    private static final Long PROBLEM_ID = 1L;
    private static final String LANGUAGE = "java";
    private static final String CODE = "public class Main {}";

    @BeforeEach
    void setUp() {
        submissionService = new SubmissionServiceImpl(
                submissionMapper, userMapper, problemMapper, queueService, realtimeService,
                contestProblemMapper, contestSubmissionMapper, contestMapper, contestParticipantMapper);
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

    private Problem createValidProblem() {
        Problem problem = new Problem();
        problem.setId(PROBLEM_ID);
        problem.setTitle("Test Problem");
        problem.setSlug("test-problem");
        return problem;
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
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(createValidProblem());
            when(userMapper.selectById(USER_ID)).thenReturn(createValidUser());
            when(submissionMapper.insert(any(Submission.class))).thenReturn(1);
            when(queueService.enqueueJudgeJob(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn("job-789");

            SubmissionVO result = submissionService.submit(USER_ID, createDTO());

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
            assertThatThrownBy(() -> submissionService.submit(null, createDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SUBMISSION_USER_ID_REQUIRED));
        }

        @Test
        @DisplayName("empty code throws SUBMISSION_CODE_EMPTY")
        void submit_emptyCode_throwsException() {
            CreateSubmissionDTO dto = createDTO();
            dto.setCode("");

            assertThatThrownBy(() -> submissionService.submit(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SUBMISSION_CODE_EMPTY));
        }

        @Test
        @DisplayName("unsupported language throws SUBMISSION_LANGUAGE_UNSUPPORTED")
        void submit_unsupportedLanguage_throwsException() {
            CreateSubmissionDTO dto = createDTO();
            dto.setLanguage("assembly");

            assertThatThrownBy(() -> submissionService.submit(USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.SUBMISSION_LANGUAGE_UNSUPPORTED));
        }

        @Test
        @DisplayName("problem not found throws PROBLEM_NOT_FOUND")
        void submit_problemNotFound_throwsException() {
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(null);

            assertThatThrownBy(() -> submissionService.submit(USER_ID, createDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.PROBLEM_NOT_FOUND));
        }

        @Test
        @DisplayName("user not found throws USER_NOT_FOUND")
        void submit_userNotFound_throwsException() {
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(createValidProblem());
            when(userMapper.selectById(USER_ID)).thenReturn(null);

            assertThatThrownBy(() -> submissionService.submit(USER_ID, createDTO()))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.USER_NOT_FOUND));
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
            Problem problem = createValidProblem();

            when(submissionMapper.selectById("sub-123")).thenReturn(submission);
            when(userMapper.selectById(USER_ID)).thenReturn(user);
            when(problemMapper.selectById(PROBLEM_ID)).thenReturn(problem);

            SubmissionVO result = submissionService.findById("sub-123", USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo("sub-123");
            assertThat(result.getUserId()).isEqualTo(USER_ID);
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
}
