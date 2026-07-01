package com.ulticode.modules.contest.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.dto.CreateContestDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.service.ContestSchedulerService;
import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.service.SubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.contest.dto.ContestListVO;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.submission.dto.CreateSubmissionDTO;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContestServiceImpl")
class ContestServiceImplTest {

    @Mock
    private ContestMapper contestMapper;
    @Mock
    private ContestProblemMapper contestProblemMapper;
    @Mock
    private ContestParticipantMapper participantMapper;
    @Mock
    private GlobalRankingMapper globalRankingMapper;
    @Mock
    private ContestAnnouncementMapper contestAnnouncementMapper;
    @Mock
    private ContestSchedulerService schedulerService;
    @Mock
    private RankingService rankingService;
    @Mock
    private AchievementTriggerService achievementTriggerService;
    @Mock
    private ContestSubmissionMapper contestSubmissionMapper;
    @Mock
    private ProblemMapper problemMapper;
    @Mock
    private SubmissionService submissionService;
    @Mock
    private SubmissionProjection submissionProjection;

    private ContestServiceImpl contestService;

    private static final String ADMIN_USER_ID = "123";
    private static final String REGULAR_USER_ID = "456";

    @BeforeEach
    void setUp() {
        contestService = new ContestServiceImpl(
                contestMapper,
                contestProblemMapper,
                participantMapper,
                globalRankingMapper,
                schedulerService,
                rankingService,
                achievementTriggerService,
                contestAnnouncementMapper,
                contestSubmissionMapper,
                problemMapper,
                submissionService,
                submissionProjection
        , null);
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
    @DisplayName("findUpcoming")
    class FindUpcomingTests {

        @Test
        @DisplayName("should use database pagination with correct filters")
        void findUpcoming_usesSelectPage() {
            Contest contest = new Contest();
            contest.setId("c1");
            contest.setSlug("upcoming-1");
            contest.setTitle("Upcoming Contest");
            contest.setStatus(ContestStatus.UPCOMING.name());
            contest.setStartTime(LocalDateTime.now().plusDays(1));
            contest.setEndTime(LocalDateTime.now().plusDays(1).plusMinutes(120));
            contest.setDurationMinutes(120);
            contest.setParticipantCount(0);
            contest.setIsVisible(true);
            contest.setIsDeleted(false);

            Page<Contest> pageResult = new Page<>(1, 20);
            pageResult.setRecords(List.of(contest));
            pageResult.setTotal(1);

            when(contestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);
            when(contestProblemMapper.countByContestIds(anyList())).thenReturn(List.of());
            when(participantMapper.findByContestIdsAndUserId(anyList(), eq(REGULAR_USER_ID))).thenReturn(List.of());

            PageResult<ContestListVO> result = contestService.findUpcoming(REGULAR_USER_ID, 1, 20);

            assertThat(result).isNotNull();
            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).title()).isEqualTo("Upcoming Contest");
            assertThat(result.getItems().get(0).status()).isEqualTo(ContestStatus.UPCOMING.name());

            verify(contestMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("should clamp page size to max 50")
        void findUpcoming_clampsPageSize() {
            Contest contest = new Contest();
            contest.setId("c1");
            contest.setSlug("upcoming-1");
            contest.setTitle("Upcoming Contest");
            contest.setStatus(ContestStatus.UPCOMING.name());
            contest.setStartTime(LocalDateTime.now().plusDays(1));
            contest.setEndTime(LocalDateTime.now().plusDays(1).plusMinutes(120));
            contest.setDurationMinutes(120);
            contest.setParticipantCount(0);
            contest.setIsVisible(true);
            contest.setIsDeleted(false);

            Page<Contest> pageResult = new Page<>(1, 50);
            pageResult.setRecords(List.of(contest));
            pageResult.setTotal(1);

            when(contestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);
            when(contestProblemMapper.countByContestIds(anyList())).thenReturn(List.of());
            when(participantMapper.findByContestIdsAndUserId(anyList(), eq(REGULAR_USER_ID))).thenReturn(List.of());

            // Request 100, should be clamped to 50
            PageResult<ContestListVO> result = contestService.findUpcoming(REGULAR_USER_ID, 1, 100);

            assertThat(result.getPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("should default page to 1 and pageSize to 20 when called with no-args overload")
        void findUpcoming_defaultsPagination() {
            Contest contest = new Contest();
            contest.setId("c1");
            contest.setSlug("upcoming-1");
            contest.setTitle("Upcoming Contest");
            contest.setStatus(ContestStatus.UPCOMING.name());
            contest.setStartTime(LocalDateTime.now().plusDays(1));
            contest.setEndTime(LocalDateTime.now().plusDays(1).plusMinutes(120));
            contest.setDurationMinutes(120);
            contest.setParticipantCount(0);
            contest.setIsVisible(true);
            contest.setIsDeleted(false);

            Page<Contest> pageResult = new Page<>(1, 20);
            pageResult.setRecords(List.of(contest));
            pageResult.setTotal(1);

            when(contestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);
            when(contestProblemMapper.countByContestIds(anyList())).thenReturn(List.of());
            when(participantMapper.findByContestIdsAndUserId(anyList(), eq(REGULAR_USER_ID))).thenReturn(List.of());

            // Calls findUpcoming(userId) which delegates to findUpcoming(userId, 1, 20)
            PageResult<ContestListVO> result = contestService.findUpcoming(REGULAR_USER_ID);

            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("findRunning")
    class FindRunningTests {

        @Test
        @DisplayName("should use database pagination with correct filters")
        void findRunning_usesSelectPage() {
            Contest contest = new Contest();
            contest.setId("c2");
            contest.setSlug("running-1");
            contest.setTitle("Running Contest");
            contest.setStatus(ContestStatus.RUNNING.name());
            contest.setStartTime(LocalDateTime.now().minusMinutes(30));
            contest.setEndTime(LocalDateTime.now().plusMinutes(90));
            contest.setDurationMinutes(120);
            contest.setParticipantCount(5);
            contest.setIsVisible(true);
            contest.setIsDeleted(false);

            Page<Contest> pageResult = new Page<>(1, 20);
            pageResult.setRecords(List.of(contest));
            pageResult.setTotal(1);

            when(contestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);
            when(contestProblemMapper.countByContestIds(anyList())).thenReturn(List.of());
            when(participantMapper.findByContestIdsAndUserId(anyList(), eq(REGULAR_USER_ID))).thenReturn(List.of());

            PageResult<ContestListVO> result = contestService.findRunning(REGULAR_USER_ID, 1, 20);

            assertThat(result).isNotNull();
            assertThat(result.getTotal()).isEqualTo(1);
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).title()).isEqualTo("Running Contest");
            assertThat(result.getItems().get(0).status()).isEqualTo(ContestStatus.RUNNING.name());

            verify(contestMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("should clamp page size to max 50")
        void findRunning_clampsPageSize() {
            Contest contest = new Contest();
            contest.setId("c2");
            contest.setSlug("running-1");
            contest.setTitle("Running Contest");
            contest.setStatus(ContestStatus.RUNNING.name());
            contest.setStartTime(LocalDateTime.now().minusMinutes(30));
            contest.setEndTime(LocalDateTime.now().plusMinutes(90));
            contest.setDurationMinutes(120);
            contest.setParticipantCount(5);
            contest.setIsVisible(true);
            contest.setIsDeleted(false);

            Page<Contest> pageResult = new Page<>(1, 50);
            pageResult.setRecords(List.of(contest));
            pageResult.setTotal(1);

            when(contestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);
            when(contestProblemMapper.countByContestIds(anyList())).thenReturn(List.of());
            when(participantMapper.findByContestIdsAndUserId(anyList(), eq(REGULAR_USER_ID))).thenReturn(List.of());

            // Request 100, should be clamped to 50
            PageResult<ContestListVO> result = contestService.findRunning(REGULAR_USER_ID, 1, 100);

            assertThat(result.getPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("should default page to 1 and pageSize to 20 when called with no-args overload")
        void findRunning_defaultsPagination() {
            Contest contest = new Contest();
            contest.setId("c2");
            contest.setSlug("running-1");
            contest.setTitle("Running Contest");
            contest.setStatus(ContestStatus.RUNNING.name());
            contest.setStartTime(LocalDateTime.now().minusMinutes(30));
            contest.setEndTime(LocalDateTime.now().plusMinutes(90));
            contest.setDurationMinutes(120);
            contest.setParticipantCount(5);
            contest.setIsVisible(true);
            contest.setIsDeleted(false);

            Page<Contest> pageResult = new Page<>(1, 20);
            pageResult.setRecords(List.of(contest));
            pageResult.setTotal(1);

            when(contestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);
            when(contestProblemMapper.countByContestIds(anyList())).thenReturn(List.of());
            when(participantMapper.findByContestIdsAndUserId(anyList(), eq(REGULAR_USER_ID))).thenReturn(List.of());

            // Calls findRunning(userId) which delegates to findRunning(userId, 1, 20)
            PageResult<ContestListVO> result = contestService.findRunning(REGULAR_USER_ID);

            assertThat(result.getPage()).isEqualTo(1);
            assertThat(result.getPageSize()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("getContestProblemSubmissions")
    class GetContestProblemSubmissionsTests {

        @Test
        @DisplayName("should return current user's submissions for contest problem")
        void getContestProblemSubmissions_success() {
            Contest contest = new Contest();
            contest.setId("contest-1");
            contest.setIsDeleted(false);

            ContestProblem contestProblem = new ContestProblem();
            contestProblem.setId("contest-problem-1");
            contestProblem.setContestId("contest-1");
            contestProblem.setProblemId(42L);

            Submission submission = new Submission();
            submission.setId("submission-1");
            submission.setProblemId(42L);
            submission.setUserId(REGULAR_USER_ID);
            submission.setStatus("Accepted");

            SubmissionVO submissionVO = new SubmissionVO();
            submissionVO.setId("submission-1");
            submissionVO.setProblemId(42L);
            submissionVO.setStatus("Accepted");

            when(contestMapper.selectById("contest-1")).thenReturn(contest);
            when(contestProblemMapper.findByContestIdAndProblemId("contest-1", 42L))
                    .thenReturn(contestProblem);
            when(contestSubmissionMapper.findSubmissionsByContestProblemAndUser(
                    "contest-1", "contest-problem-1", REGULAR_USER_ID))
                    .thenReturn(List.of(submission));
            when(submissionProjection.toVO(submission)).thenReturn(submissionVO);

            List<SubmissionVO> result = contestService.getContestProblemSubmissions(
                    "contest-1", 42L, REGULAR_USER_ID);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo("submission-1");
            assertThat(result.get(0).getStatus()).isEqualTo("Accepted");
        }

        @Test
        @DisplayName("should throw when problem does not belong to contest")
        void getContestProblemSubmissions_problemNotInContest() {
            Contest contest = new Contest();
            contest.setId("contest-1");
            contest.setIsDeleted(false);

            when(contestMapper.selectById("contest-1")).thenReturn(contest);
            when(contestProblemMapper.findByContestIdAndProblemId("contest-1", 42L))
                    .thenReturn(null);

            assertThatThrownBy(() -> contestService.getContestProblemSubmissions(
                    "contest-1", 42L, REGULAR_USER_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PROBLEM_NOT_FOUND);

            verify(contestSubmissionMapper, never())
                    .findSubmissionsByContestProblemAndUser(any(), any(), any());
        }

        @Test
        @DisplayName("should return empty list when user has no submissions for contest problem")
        void getContestProblemSubmissions_noSubmissions_returnsEmptyList() {
            Contest contest = new Contest();
            contest.setId("contest-1");
            contest.setIsDeleted(false);

            ContestProblem contestProblem = new ContestProblem();
            contestProblem.setId("contest-problem-1");
            contestProblem.setContestId("contest-1");
            contestProblem.setProblemId(42L);

            when(contestMapper.selectById("contest-1")).thenReturn(contest);
            when(contestProblemMapper.findByContestIdAndProblemId("contest-1", 42L))
                    .thenReturn(contestProblem);
            when(contestSubmissionMapper.findSubmissionsByContestProblemAndUser(
                    "contest-1", "contest-problem-1", REGULAR_USER_ID))
                    .thenReturn(List.of());

            List<SubmissionVO> result = contestService.getContestProblemSubmissions(
                    "contest-1", 42L, REGULAR_USER_ID);

            assertThat(result).isEmpty();
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
            participant.setStartedAt(java.time.LocalDateTime.now().minusMinutes(90));

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
