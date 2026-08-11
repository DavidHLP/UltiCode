package com.ulticode.modules.contest.projection;

import com.ulticode.app.error.ContestErrorCode;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.app.api.service.SubmissionReadPort;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.contest.service.RankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultContestProjectionVisibilityTest {

    @Mock private ContestMapper contestMapper;
    @Mock private ContestProblemMapper contestProblemMapper;
    @Mock private ContestParticipantMapper participantMapper;
    @Mock private ContestSubmissionMapper contestSubmissionMapper;
    @Mock private GlobalRankingMapper globalRankingMapper;
    @Mock private ContestAnnouncementMapper contestAnnouncementMapper;
    @Mock private ProblemFactsPort problemFactsPort;
    @Mock private RankingService rankingService;
    @Mock private SubmissionReadPort submissionProjection;

    private DefaultContestProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultContestProjection(
                contestMapper, contestProblemMapper, participantMapper,
                contestSubmissionMapper, globalRankingMapper, contestAnnouncementMapper,
                problemFactsPort, rankingService, submissionProjection);
    }

    @Test
    void publicDetailHidesInvisibleContest() {
        when(contestMapper.selectById("draft")).thenReturn(contest("draft", false));

        assertThatThrownBy(() -> projection.getPublicContestById("draft", null))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_NOT_FOUND);
    }

    @Test
    void adminDetailStillReadsInvisibleContest() {
        when(contestMapper.selectById("draft")).thenReturn(contest("draft", false));

        assertThat(projection.getContestById("draft", null).getId()).isEqualTo("draft");
    }

    @Test
    void publicSubresourcesHideInvisibleContest() {
        when(contestMapper.selectById("draft")).thenReturn(contest("draft", false));

        assertThatThrownBy(() -> projection.getContestProblems("draft"))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_NOT_FOUND);
        assertThatThrownBy(() -> projection.getContestAnnouncements("draft"))
                .isInstanceOf(com.ulticode.common.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ContestErrorCode.CONTEST_NOT_FOUND);
    }

    private static Contest contest(String id, boolean visible) {
        Contest contest = new Contest();
        contest.setId(id);
        contest.setSlug(id);
        contest.setTitle(id);
        contest.setIsVisible(visible);
        contest.setIsDeleted(false);
        return contest;
    }
}
