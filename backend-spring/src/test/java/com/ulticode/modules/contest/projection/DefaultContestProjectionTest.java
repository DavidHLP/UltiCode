package com.ulticode.modules.contest.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.modules.contest.dto.ContestListVO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.dto.SubmissionVO;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultContestProjection} — the read-side deep module
 * lifted out of ContestServiceImpl. Covers the list-query pagination/clamping
 * behaviour, the contest-problem-submissions projection, and the admin-ranking
 * 404 guard. These cases previously lived on ContestServiceImplTest /
 * ContestServiceImplMutatorTest and were migrated verbatim when the read
 * cluster moved behind the projection seam.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultContestProjection")
class DefaultContestProjectionTest {

    @Mock private ContestMapper contestMapper;
    @Mock private ContestProblemMapper contestProblemMapper;
    @Mock private ContestParticipantMapper participantMapper;
    @Mock private ContestSubmissionMapper contestSubmissionMapper;
    @Mock private GlobalRankingMapper globalRankingMapper;
    @Mock private ContestAnnouncementMapper contestAnnouncementMapper;
    @Mock private ProblemMapper problemMapper;
    @Mock private RankingService rankingService;
    @Mock private SubmissionProjection submissionProjection;

    private static final String REGULAR_USER_ID = "456";

    private DefaultContestProjection projection;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        projection = new DefaultContestProjection(
                contestMapper, contestProblemMapper, participantMapper,
                contestSubmissionMapper, globalRankingMapper, contestAnnouncementMapper,
                problemMapper, rankingService, submissionProjection);
    }

    @Nested
    @DisplayName("findUpcoming")
    class FindUpcomingTests {

        @Test
        @DisplayName("should use database pagination with correct filters")
        void findUpcoming_usesSelectPage() {
            Contest contest = upcomingContest();

            Page<Contest> pageResult = new Page<>(1, 20);
            pageResult.setRecords(List.of(contest));
            pageResult.setTotal(1);

            when(contestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);
            when(contestProblemMapper.countByContestIds(anyList())).thenReturn(List.of());
            when(participantMapper.findByContestIdsAndUserId(anyList(), eq(REGULAR_USER_ID))).thenReturn(List.of());

            PageResult<ContestListVO> result = projection.findUpcoming(REGULAR_USER_ID, 1, 20);

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
            Contest contest = upcomingContest();

            Page<Contest> pageResult = new Page<>(1, 50);
            pageResult.setRecords(List.of(contest));
            pageResult.setTotal(1);

            when(contestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);
            when(contestProblemMapper.countByContestIds(anyList())).thenReturn(List.of());
            when(participantMapper.findByContestIdsAndUserId(anyList(), eq(REGULAR_USER_ID))).thenReturn(List.of());

            // Request 100, should be clamped to 50
            PageResult<ContestListVO> result = projection.findUpcoming(REGULAR_USER_ID, 1, 100);

            assertThat(result.getPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("should default page to 1 and pageSize to 20 when called with no-args overload")
        void findUpcoming_defaultsPagination() {
            Contest contest = upcomingContest();

            Page<Contest> pageResult = new Page<>(1, 20);
            pageResult.setRecords(List.of(contest));
            pageResult.setTotal(1);

            when(contestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);
            when(contestProblemMapper.countByContestIds(anyList())).thenReturn(List.of());
            when(participantMapper.findByContestIdsAndUserId(anyList(), eq(REGULAR_USER_ID))).thenReturn(List.of());

            // Calls findUpcoming(userId) which delegates to findUpcoming(userId, 1, 20)
            PageResult<ContestListVO> result = projection.findUpcoming(REGULAR_USER_ID);

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
            Contest contest = runningContest();

            Page<Contest> pageResult = new Page<>(1, 20);
            pageResult.setRecords(List.of(contest));
            pageResult.setTotal(1);

            when(contestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);
            when(contestProblemMapper.countByContestIds(anyList())).thenReturn(List.of());
            when(participantMapper.findByContestIdsAndUserId(anyList(), eq(REGULAR_USER_ID))).thenReturn(List.of());

            PageResult<ContestListVO> result = projection.findRunning(REGULAR_USER_ID, 1, 20);

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
            Contest contest = runningContest();

            Page<Contest> pageResult = new Page<>(1, 50);
            pageResult.setRecords(List.of(contest));
            pageResult.setTotal(1);

            when(contestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);
            when(contestProblemMapper.countByContestIds(anyList())).thenReturn(List.of());
            when(participantMapper.findByContestIdsAndUserId(anyList(), eq(REGULAR_USER_ID))).thenReturn(List.of());

            PageResult<ContestListVO> result = projection.findRunning(REGULAR_USER_ID, 1, 100);

            assertThat(result.getPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("should default page to 1 and pageSize to 20 when called with no-args overload")
        void findRunning_defaultsPagination() {
            Contest contest = runningContest();

            Page<Contest> pageResult = new Page<>(1, 20);
            pageResult.setRecords(List.of(contest));
            pageResult.setTotal(1);

            when(contestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(pageResult);
            when(contestProblemMapper.countByContestIds(anyList())).thenReturn(List.of());
            when(participantMapper.findByContestIdsAndUserId(anyList(), eq(REGULAR_USER_ID))).thenReturn(List.of());

            PageResult<ContestListVO> result = projection.findRunning(REGULAR_USER_ID);

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

            List<SubmissionVO> result = projection.getContestProblemSubmissions(
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

            assertThatThrownBy(() -> projection.getContestProblemSubmissions(
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

            List<SubmissionVO> result = projection.getContestProblemSubmissions(
                    "contest-1", 42L, REGULAR_USER_ID);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getAdminContestRanking")
    class GetAdminContestRankingTests {

        @Test
        @DisplayName("缺陷 #5: 不存在 contest 返 404 而非 200+空")
        void getAdminContestRanking_nonExistentContest_throwsNotFound() {
            when(contestMapper.selectById("fake-id")).thenReturn(null);

            assertThatThrownBy(() -> projection.getAdminContestRanking("fake-id", 1, 50))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CONTEST_NOT_FOUND);
        }
    }

    private Contest upcomingContest() {
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
        return contest;
    }

    private Contest runningContest() {
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
        return contest;
    }
}
