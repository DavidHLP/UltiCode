package com.ulticode.modules.contest.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.uuid.FixedUuidGenerator;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.projection.ContestProjection;
import com.ulticode.modules.contest.service.ContestScoringService;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.service.SubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.ulticode.common.auth.CurrentUserProvider;

/**
 * Write-side unit tests for {@link ContestServiceImpl}. The read-cluster cases
 * (findUpcoming / findRunning / getContestProblemSubmissions) moved to
 * {@link com.ulticode.modules.contest.projection.DefaultContestProjectionTest}
 * when the read paths were lifted into {@link ContestProjection}. What remains
 * here is the contest state machine: createContest field/slug/end-time
 * computation (its return value is shaped by a mocked projection.toVO echo) and
 * the submitContestProblem guard matrix.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ContestServiceImpl")
class ContestServiceImplTest {

    @Mock private ContestMapper contestMapper;
    @Mock private ContestProblemMapper contestProblemMapper;
    @Mock private ContestParticipantMapper participantMapper;
    @Mock private SubmissionService submissionService;
    @Mock private ContestScoringService contestScoringService;
    @Mock private ContestProjection contestProjection;
    @Mock private com.ulticode.modules.contest.clock.ContestClock contestClock;
    @Mock private Clock clock;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private ContestServiceImpl contestService;

    private static final String ADMIN_USER_ID = "123";
    private static final String REGULAR_USER_ID = "456";

    @BeforeEach
    void setUp() {
        when(currentUserProvider.getCurrentUserId()).thenReturn("test-user-id");
        when(currentUserProvider.hasAnyRole("ADMIN", "SUPER_ADMIN")).thenReturn(true);
        contestService = new ContestServiceImpl(
                contestMapper,
                contestProblemMapper,
                participantMapper,
                submissionService,
                contestScoringService,
                contestProjection,
                contestClock,
                clock,
                new FixedUuidGenerator(), currentUserProvider);
        org.mockito.Mockito.lenient().when(clock.instant()).thenReturn(LocalDateTime.of(2024, 6, 1, 0, 0).atZone(ZoneId.systemDefault()).toInstant());
        org.mockito.Mockito.lenient().when(clock.getZone()).thenReturn(ZoneId.systemDefault());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAdminAuthentication() {
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                ADMIN_USER_ID,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void setRegularUserAuthentication() {
        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                REGULAR_USER_ID,
                null,
                Collections.emptyList()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Echo the contest entity into a ContestVO using the same field shape the
     * real projection applies, so the createContest assertions observe the
     * contest built by the service without depending on projection internals.
     */
    private void stubToVoEcho() {
        when(contestProjection.toVO(any(Contest.class), any())).thenAnswer(inv -> {
            Contest c = inv.getArgument(0);
            ContestVO vo = new ContestVO();
            BeanUtils.copyProperties(c, vo);
            vo.setDuration(c.getDurationMinutes());
            vo.setIsPublished(c.getIsVisible());
            vo.setCurrentParticipants(c.getParticipantCount());
            return vo;
        });
    }

    private CreateContestDTO createValidDTO() {
        CreateContestDTO dto = new CreateContestDTO();
        dto.setTitle("Weekly Contest #123");
        dto.setDescription("Test contest description");
        dto.setStartTime(LocalDateTime.now().plusDays(1));
        dto.setDuration(120);
        dto.setMaxParticipants(1000);
        dto.setIsPublished(true);
        return dto;
    }

    @Nested
    @DisplayName("createContest")
    class CreateContestTests {

        @Test
        @DisplayName("should create contest successfully when user is admin")
        void createContest_asAdmin_success() {
            setAdminAuthentication();
            stubToVoEcho();

            CreateContestDTO dto = createValidDTO();
            when(contestMapper.insert(any(Contest.class))).thenReturn(1);

            ContestVO result = contestService.createContest(dto, ADMIN_USER_ID);

            assertThat(result).isNotNull();
            assertThat(result.getTitle()).isEqualTo("Weekly Contest #123");
            assertThat(result.getDescription()).isEqualTo("Test contest description");
            assertThat(result.getDuration()).isEqualTo(120);
            assertThat(result.getMaxParticipants()).isEqualTo(1000);
            assertThat(result.getIsPublished()).isTrue();
            // P0-3 fix: isPublished=true -> status=UPCOMING (was DRAFT before).
            assertThat(result.getStatus()).isEqualTo("UPCOMING");
            assertThat(result.getCurrentParticipants()).isEqualTo(0);

            verify(contestMapper).insert(any(Contest.class));
            clearAuthentication();
        }

        @Test
        @DisplayName("should throw BusinessException when user is not admin")
        void createContest_asNonAdmin_forbidden() {
        when(currentUserProvider.hasAnyRole("ADMIN", "SUPER_ADMIN")).thenReturn(false);
            setRegularUserAuthentication();

            CreateContestDTO dto = createValidDTO();

            assertThatThrownBy(() -> contestService.createContest(dto, REGULAR_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

            verify(contestMapper, never()).insert(any(Contest.class));
            clearAuthentication();
        }

        @Test
        @DisplayName("should throw BusinessException when user is not authenticated")
        void createContest_unauthenticated_forbidden() {
        when(currentUserProvider.hasAnyRole("ADMIN", "SUPER_ADMIN")).thenReturn(false);
        when(currentUserProvider.getCurrentUserId()).thenReturn(null);
            clearAuthentication();

            CreateContestDTO dto = createValidDTO();

            assertThatThrownBy(() -> contestService.createContest(dto, null))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

            verify(contestMapper, never()).insert(any(Contest.class));
        }

        @Test
        @DisplayName("should set default values correctly")
        void createContest_defaultValues() {
            setAdminAuthentication();
            stubToVoEcho();

            CreateContestDTO dto = createValidDTO();
            dto.setIsPublished(null);

            when(contestMapper.insert(any(Contest.class))).thenReturn(1);

            ContestVO result = contestService.createContest(dto, ADMIN_USER_ID);

            assertThat(result.getIsPublished()).isFalse();
            assertThat(result.getStatus()).isEqualTo("DRAFT");
            assertThat(result.getCurrentParticipants()).isEqualTo(0);

            verify(contestMapper).insert((Contest) argThat(contest ->
                    ((Contest) contest).getRegisteredCount() == 0 &&
                    ((Contest) contest).getParticipantCount() == 0 &&
                    ((Contest) contest).getSubmissionCount() == 0 &&
                    Boolean.FALSE.equals(((Contest) contest).getIsDeleted())
            ));
            clearAuthentication();
        }

        @Test
        @DisplayName("should generate slug from title")
        void createContest_generatesSlug() {
            setAdminAuthentication();
            stubToVoEcho();

            CreateContestDTO dto = createValidDTO();
            dto.setTitle("Test Contest Title!");

            when(contestMapper.insert(any(Contest.class))).thenReturn(1);

            ContestVO result = contestService.createContest(dto, ADMIN_USER_ID);

            assertThat(result.getSlug()).isEqualTo("test-contest-title");
            clearAuthentication();
        }

        @Test
        @DisplayName("should calculate end time from start time and duration")
        void createContest_calculatesEndTime() {
            setAdminAuthentication();
            stubToVoEcho();

            LocalDateTime startTime = LocalDateTime.of(2024, 12, 31, 10, 0);
            CreateContestDTO dto = createValidDTO();
            dto.setStartTime(startTime);
            dto.setDuration(120);

            when(contestMapper.insert(any(Contest.class))).thenReturn(1);

            ContestVO result = contestService.createContest(dto, ADMIN_USER_ID);

            assertThat(result.getEndTime()).isEqualTo(startTime.plusMinutes(120));
            clearAuthentication();
        }
    }

    @Nested
    @DisplayName("submitContestProblem")
    class SubmitContestProblemTests {

        @Test
        @DisplayName("should submit when contest is running and participant has started")
        void submitContestProblem_success() {
            Contest contest = new Contest();
            contest.setId("contest-1");
            contest.setStatus(ContestStatus.RUNNING.name());
            contest.setIsDeleted(false);

            ContestProblem contestProblem = new ContestProblem();
            contestProblem.setId("contest-problem-1");
            contestProblem.setContestId("contest-1");
            contestProblem.setProblemId(42L);

            ContestParticipant participant = new ContestParticipant();
            participant.setId("participant-1");
            participant.setContestId("contest-1");
            participant.setUserId(REGULAR_USER_ID);
            participant.setStatus(ContestParticipantStatus.STARTED.name());

            CreateSubmissionDTO dto = new CreateSubmissionDTO();
            dto.setLanguage("java");
            dto.setCode("class Main {}");

            SubmissionVO submissionVO = new SubmissionVO();
            submissionVO.setId("submission-1");
            submissionVO.setProblemId(42L);

            when(contestMapper.selectById("contest-1")).thenReturn(contest);
            when(contestProblemMapper.findByContestIdAndProblemId("contest-1", 42L))
                    .thenReturn(contestProblem);
            when(participantMapper.findByContestIdAndUserId("contest-1", REGULAR_USER_ID))
                    .thenReturn(java.util.Optional.of(participant));
            when(submissionService.submit(REGULAR_USER_ID, dto)).thenReturn(submissionVO);

            SubmissionVO result = contestService.submitContestProblem(
                    "contest-1", 42L, REGULAR_USER_ID, dto);

            assertThat(result.getId()).isEqualTo("submission-1");
            assertThat(dto.getProblemId()).isEqualTo(42L);
        }

        @Test
        @DisplayName("should reject when participant has not started")
        void submitContestProblem_notStarted() {
            Contest contest = new Contest();
            contest.setId("contest-1");
            contest.setStatus(ContestStatus.RUNNING.name());
            contest.setIsDeleted(false);

            ContestProblem contestProblem = new ContestProblem();
            contestProblem.setId("contest-problem-1");
            contestProblem.setContestId("contest-1");
            contestProblem.setProblemId(42L);

            ContestParticipant participant = new ContestParticipant();
            participant.setId("participant-1");
            participant.setContestId("contest-1");
            participant.setUserId(REGULAR_USER_ID);
            participant.setStatus(ContestParticipantStatus.REGISTERED.name());

            CreateSubmissionDTO dto = new CreateSubmissionDTO();
            dto.setLanguage("java");
            dto.setCode("class Main {}");

            when(contestMapper.selectById("contest-1")).thenReturn(contest);
            when(contestProblemMapper.findByContestIdAndProblemId("contest-1", 42L))
                    .thenReturn(contestProblem);
            when(participantMapper.findByContestIdAndUserId("contest-1", REGULAR_USER_ID))
                    .thenReturn(java.util.Optional.of(participant));

            assertThatThrownBy(() -> contestService.submitContestProblem(
                    "contest-1", 42L, REGULAR_USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_NOT_STARTED);

            verify(submissionService, never()).submit(any(), any());
        }

        /** R6.2 / F-07: virtual sessions are rejected once the participant
         *  has been playing longer than contest.durationMinutes, even if
         *  the contest itself is still RUNNING. Locks the server-side
         *  hard cutoff so the auto-finish scheduler lag (10s tick) can't
         *  leak late submissions through. */
        @Test
        @DisplayName("R6.2 / F-07: virtual session past duration is rejected")
        void submitContestProblem_virtualSessionPastDuration_rejected() {
            Contest contest = new Contest();
            contest.setId("contest-1");
            contest.setStatus(ContestStatus.RUNNING.name());
            contest.setIsDeleted(false);
            contest.setDurationMinutes(60);
            contest.setEndTime(java.time.LocalDateTime.now().plusHours(2));

            ContestProblem contestProblem = new ContestProblem();
            contestProblem.setId("contest-problem-1");
            contestProblem.setContestId("contest-1");
            contestProblem.setProblemId(42L);

            ContestParticipant participant = new ContestParticipant();
            participant.setId("participant-1");
            participant.setContestId("contest-1");
            participant.setUserId(REGULAR_USER_ID);
            participant.setStatus(ContestParticipantStatus.STARTED.name());
            participant.setIsVirtual(true);
            // Started 90 min ago for a 60-min virtual contest: past hard deadline.
            participant.setStartedAt(LocalDateTime.of(2024, 6, 1, 0, 0).minusMinutes(90));

            CreateSubmissionDTO dto = new CreateSubmissionDTO();
            dto.setLanguage("java");
            dto.setCode("class Main {}");

            when(contestMapper.selectById("contest-1")).thenReturn(contest);
            when(contestProblemMapper.findByContestIdAndProblemId("contest-1", 42L))
                    .thenReturn(contestProblem);
            when(participantMapper.findByContestIdAndUserId("contest-1", REGULAR_USER_ID))
                    .thenReturn(java.util.Optional.of(participant));

            when(contestClock.effectiveEndTime(any(), any()))
                    .thenReturn(java.util.Optional.of(LocalDateTime.of(2024, 6, 1, 0, 0).minusMinutes(30)));

            assertThatThrownBy(() -> contestService.submitContestProblem(
                    "contest-1", 42L, REGULAR_USER_ID, dto))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_ENDED);

            verify(submissionService, never()).submit(any(), any());
        }

        /** R6.2 / F-07: virtual session within duration is accepted.
         *  Locks the inverse — we don't want false positives. */
        @Test
        @DisplayName("R6.2 / F-07: virtual session within duration is accepted")
        void submitContestProblem_virtualSessionWithinDuration_accepted() {
            Contest contest = new Contest();
            contest.setId("contest-1");
            contest.setStatus(ContestStatus.RUNNING.name());
            contest.setIsDeleted(false);
            contest.setDurationMinutes(60);
            contest.setEndTime(java.time.LocalDateTime.now().plusHours(2));

            ContestProblem contestProblem = new ContestProblem();
            contestProblem.setId("contest-problem-1");
            contestProblem.setContestId("contest-1");
            contestProblem.setProblemId(42L);

            ContestParticipant participant = new ContestParticipant();
            participant.setId("participant-1");
            participant.setContestId("contest-1");
            participant.setUserId(REGULAR_USER_ID);
            participant.setStatus(ContestParticipantStatus.STARTED.name());
            participant.setIsVirtual(true);
            // Started 10 min ago: well within the 60-min virtual budget.
            participant.setStartedAt(java.time.LocalDateTime.now().minusMinutes(10));

            CreateSubmissionDTO dto = new CreateSubmissionDTO();
            dto.setLanguage("java");
            dto.setCode("class Main {}");

            SubmissionVO submissionVO = new SubmissionVO();
            submissionVO.setId("submission-1");

            when(contestMapper.selectById("contest-1")).thenReturn(contest);
            when(contestProblemMapper.findByContestIdAndProblemId("contest-1", 42L))
                    .thenReturn(contestProblem);
            when(participantMapper.findByContestIdAndUserId("contest-1", REGULAR_USER_ID))
                    .thenReturn(java.util.Optional.of(participant));
            when(submissionService.submit(REGULAR_USER_ID, dto)).thenReturn(submissionVO);

            SubmissionVO result = contestService.submitContestProblem(
                    "contest-1", 42L, REGULAR_USER_ID, dto);
            assertThat(result.getId()).isEqualTo("submission-1");
        }
    }
}
