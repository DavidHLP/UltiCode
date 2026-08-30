package com.ulticode.modules.contest.projection;

import com.ulticode.app.error.ContestErrorCode;
import com.ulticode.app.api.service.ProblemFactsPort;
import com.ulticode.submission.api.service.SubmissionReadPort;
import com.ulticode.app.api.service.SubmissionUserReadPort;
import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.ContestProblem;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.submission.api.dto.SubmissionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
    @Mock private SubmissionUserReadPort submissionUserReadPort;

    private DefaultContestProjection projection;

    @BeforeEach
    void setUp() {
        projection = new DefaultContestProjection(
                contestMapper, contestProblemMapper, participantMapper,
                contestSubmissionMapper, globalRankingMapper, contestAnnouncementMapper,
                problemFactsPort, rankingService, submissionProjection, submissionUserReadPort);
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

    @Test
    void contestDetailUsesRealParticipantWhenVirtualReplayAlsoExists() {
        Contest contest = contest("running", true);
        ContestParticipant real = participant("real", 7, 120, false);
        ContestParticipant virtual = participant("virtual", 1, 999, true);
        when(contestProblemMapper.countByContestId("running")).thenReturn(3L);
        when(participantMapper.findRealByContestIdAndUserId("running", "user-1"))
                .thenReturn(Optional.of(real));
        lenient().when(participantMapper.findByContestIdAndUserId("running", "user-1"))
                .thenReturn(Optional.of(virtual));

        ContestVO result = projection.toVO(contest, "user-1");

        assertThat(result.getIsParticipating()).isTrue();
        assertThat(result.getUserRanking()).isEqualTo(7);
        assertThat(result.getUserScore()).isEqualTo(120L);
    }

    @Test
    void contestSubmissionReadUsesOneBatchProjection() {
        when(contestMapper.selectById("running")).thenReturn(contest("running", true));
        ContestProblem contestProblem = new ContestProblem();
        contestProblem.setId("cp-1");
        contestProblem.setContestId("running");
        contestProblem.setProblemId(7L);
        when(contestProblemMapper.findByContestIdAndProblemId("running", 7L))
                .thenReturn(contestProblem);

        when(contestSubmissionMapper.findSubmissionIdsByContestProblemAndUser(
                "running", "cp-1", "user-1", 100))
                .thenReturn(List.of("submission-1", "submission-2"));
        SubmissionVO first = new SubmissionVO();
        first.setUserId("user-1");
        SubmissionVO second = new SubmissionVO();
        second.setUserId("user-1");
        when(submissionProjection.toVOs(any())).thenReturn(List.of(first, second));

        assertThat(projection.getContestProblemSubmissions("running", 7L, "user-1"))
                .hasSize(2);
        verify(submissionProjection, times(1)).toVOs(any());
        verify(submissionProjection, never()).toVO(any(String.class));
    }

    private static ContestParticipant participant(String id, int rank, int score, boolean virtual) {
        ContestParticipant participant = new ContestParticipant();
        participant.setId(id);
        participant.setContestId("running");
        participant.setUserId("user-1");
        participant.setFinalRank(rank);
        participant.setTotalScore(score);
        participant.setIsVirtual(virtual);
        return participant;
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
