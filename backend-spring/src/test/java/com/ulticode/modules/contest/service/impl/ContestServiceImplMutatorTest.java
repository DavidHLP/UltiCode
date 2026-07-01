package com.ulticode.modules.contest.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.contest.dto.AddContestProblemDTO;
import com.ulticode.modules.contest.dto.UpdateContestDTO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.entity.enums.ContestStatus;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.contest.service.ContestSchedulerService;
import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.modules.achievement.service.AchievementTriggerService;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.submission.projection.SubmissionProjection;
import com.ulticode.modules.submission.service.SubmissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Smoke test for the 7 admin contest mutator fixes (see admin-contests-mutator-fixes.plan.md).
 * Uses pure Mockito (no Spring context) — sufficient for service-layer guard logic
 * but does NOT cover the @Audited AOP weaving or LambdaUpdateWrapper SQL fragment
 * (both need @SpringBootTest). deleteContest_persistsAllThreeFields is covered by
 * the runtime curl test in docs/contests-api-test-report.md §3.5.
 *
 * Covers defects #2 (updateContest UPCOMING guard), #5 (rankings 404),
 * #6 (mutators reject is_deleted contests).
 */
@ExtendWith(MockitoExtension.class)
class ContestServiceImplMutatorTest {

    @Mock ContestMapper contestMapper;
    @Mock ContestProblemMapper contestProblemMapper;
    @Mock ContestParticipantMapper participantMapper;
    @Mock GlobalRankingMapper globalRankingMapper;
    @Mock ContestAnnouncementMapper contestAnnouncementMapper;
    @Mock ContestSubmissionMapper contestSubmissionMapper;
    @Mock ContestSchedulerService schedulerService;
    @Mock RankingService rankingService;
    @Mock AchievementTriggerService achievementTriggerService;
    @Mock ProblemMapper problemMapper;
    @Mock SubmissionService submissionService;
    @Mock SubmissionProjection submissionProjection;

    ContestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ContestServiceImpl(
                contestMapper, contestProblemMapper, participantMapper,
                globalRankingMapper, schedulerService, rankingService,
                achievementTriggerService, contestAnnouncementMapper,
                contestSubmissionMapper, problemMapper, submissionService,
                submissionProjection, null);
        // Mock authentication with ROLE_ADMIN so SecurityUtil.hasAnyRole passes
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "9f6bc78a-5f21-11f1-950a-8ef0eeeb1ca8", "n/a",
                        List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Contest newContest(String id, ContestStatus status, boolean isDeleted) {
        Contest c = new Contest();
        c.setId(id);
        c.setTitle("Test Contest");
        c.setStatus(status.name());
        c.setIsDeleted(isDeleted);
        c.setCreatedBy("9f6bc78a-5f21-11f1-950a-8ef0eeeb1ca8");
        c.setStartTime(LocalDateTime.now().plusDays(1));
        c.setDurationMinutes(120);
        c.setSlug("test-contest-" + id);
        return c;
    }

    @Test
    @DisplayName("缺陷 #1 + 幂等性: 第二次 DELETE 抛 CONTEST_NOT_FOUND (与 AdminContestServiceImpl 行为一致)")
    void deleteContest_alreadyDeleted_throwsNotFound() {
        Contest c = newContest("c-2", ContestStatus.UPCOMING, true);
        when(contestMapper.selectById("c-2")).thenReturn(c);

        assertThatThrownBy(() -> service.deleteContest("c-2"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONTEST_NOT_FOUND);
        verify(contestMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("缺陷 #2: updateContest 拒 non-UPCOMING, 抛新 ErrorCode CONTEST_ONLY_UPDATE_UPCOMING (70006)")
    void updateContest_finishedStatus_throwsUpdateError() {
        Contest c = newContest("c-3", ContestStatus.FINISHED, false);
        when(contestMapper.selectById("c-3")).thenReturn(c);
        UpdateContestDTO dto = new UpdateContestDTO();
        dto.setTitle("renamed");

        assertThatThrownBy(() -> service.updateContest("c-3", dto))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONTEST_ONLY_UPDATE_UPCOMING);
    }

    @Test
    @DisplayName("缺陷 #6: addProblem 拒已软删除 contest")
    void addProblem_deletedContest_throwsNotFound() {
        Contest c = newContest("c-4", ContestStatus.UPCOMING, true);
        when(contestMapper.selectById("c-4")).thenReturn(c);
        AddContestProblemDTO dto = new AddContestProblemDTO();
        dto.setProblemId(1L);

        assertThatThrownBy(() -> service.addProblem("c-4", dto))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONTEST_NOT_FOUND);
        verify(contestProblemMapper, never()).insert(any(ContestProblem.class));
    }

    @Test
    @DisplayName("缺陷 #6: startContest 拒已软删除 contest")
    void startContest_deletedContest_throwsNotFound() {
        Contest c = newContest("c-5", ContestStatus.UPCOMING, true);
        when(contestMapper.selectById("c-5")).thenReturn(c);

        assertThatThrownBy(() -> service.startContest("c-5", "9f6bc78a-..."))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONTEST_NOT_FOUND);
    }

    @Test
    @DisplayName("缺陷 #5: getAdminContestRanking 不存在 contest 返 404 而非 200+空")
    void getAdminContestRanking_nonExistentContest_throwsNotFound() {
        when(contestMapper.selectById("fake-id")).thenReturn(null);

        assertThatThrownBy(() -> service.getAdminContestRanking("fake-id", 1, 50))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.CONTEST_NOT_FOUND);
    }
}
