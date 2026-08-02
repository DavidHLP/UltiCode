package com.ulticode.modules.contest.projection;

import com.ulticode.modules.contest.dto.ContestVO;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.entity.enums.ContestParticipantStatus;
import com.ulticode.modules.contest.mapper.ContestAnnouncementMapper;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.contest.mapper.ContestProblemMapper;
import com.ulticode.modules.contest.mapper.ContestSubmissionMapper;
import com.ulticode.modules.contest.mapper.GlobalRankingMapper;
import com.ulticode.modules.contest.service.RankingService;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.app.api.service.SubmissionReadPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Contest Participation projection's batched
 * user-contest history read. Pins the new behavior so future drift
 * (e.g. someone re-introducing the N+1 re-read loop) fails the build.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultContestProjection.findUserContests — deep seam")
class DefaultContestProjectionUserContestsTest {

    @Mock private ContestMapper contestMapper;
    @Mock private ContestProblemMapper contestProblemMapper;
    @Mock private ContestParticipantMapper participantMapper;
    @Mock private ContestSubmissionMapper contestSubmissionMapper;
    @Mock private GlobalRankingMapper globalRankingMapper;
    @Mock private ContestAnnouncementMapper contestAnnouncementMapper;
    @Mock private ProblemMapper problemMapper;
    @Mock private RankingService rankingService;
    @Mock private SubmissionReadPort submissionProjection;

    private DefaultContestProjection projection;

    private static final String USER_ID = "user-001";
    private static final String C1 = "contest-1";
    private static final String C2 = "contest-2";
    private static final String C3 = "contest-3";

    @BeforeEach
    void setUp() {
        projection = new DefaultContestProjection(
                contestMapper, contestProblemMapper, participantMapper,
                contestSubmissionMapper, globalRankingMapper, contestAnnouncementMapper,
                problemMapper, rankingService, submissionProjection);
    }

    private ContestParticipant makeParticipant(String contestId, String status, boolean isVirtual) {
        ContestParticipant p = new ContestParticipant();
        p.setId("p-" + contestId);
        p.setContestId(contestId);
        p.setUserId(USER_ID);
        p.setStatus(status);
        p.setIsVirtual(isVirtual);
        p.setTotalScore(100);
        p.setFinalRank(1);
        return p;
    }

    private Contest makeContest(String id) {
        Contest c = new Contest();
        c.setId(id);
        c.setTitle("Contest " + id);
        c.setSlug(id);
        c.setStatus("RUNNING");
        c.setDurationMinutes(120);
        c.setContestType("ICPC");
        c.setParticipantCount(0);
        c.setIsVisible(true);
        c.setIsDeleted(false);
        return c;
    }

    @Test
    @DisplayName("returns empty list when userId is null/blank")
    void emptyWhenUserIdMissing() {
        assertThat(projection.findUserContests(null, "registered")).isEmpty();
        assertThat(projection.findUserContests("", "registered")).isEmpty();
        assertThat(projection.findUserContests("   ", "registered")).isEmpty();
    }

    @Test
    @DisplayName("returns empty list when user has no participations")
    void emptyWhenNoParticipations() {
        when(participantMapper.findByUserId(USER_ID)).thenReturn(new ArrayList<>());
        assertThat(projection.findUserContests(USER_ID, "registered")).isEmpty();
        // no further reads happen
        verify(contestMapper, never()).selectBatchIds(any());
    }

    @Test
    @DisplayName("filters by REGISTERED when type='registered'")
    void filtersRegistered() {
        ContestParticipant registered1 = makeParticipant(C1, ContestParticipantStatus.REGISTERED.wireValue(), false);
        ContestParticipant started = makeParticipant(C2, ContestParticipantStatus.STARTED.wireValue(), false);
        ContestParticipant virtual = makeParticipant(C3, ContestParticipantStatus.STARTED.wireValue(), true);
        when(participantMapper.findByUserId(USER_ID)).thenReturn(List.of(registered1, started, virtual));
        when(contestMapper.selectBatchIds(any())).thenReturn(List.of(makeContest(C1)));
        when(contestProblemMapper.countByContestId(C1)).thenReturn(5L);

        List<ContestVO> result = projection.findUserContests(USER_ID, "registered");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(C1);
        assertThat(result.get(0).getIsParticipating()).isTrue();
        assertThat(result.get(0).getUserScore()).isEqualTo(100L);
    }

    @Test
    @DisplayName("filters by virtual sessions when type='virtual'")
    void filtersVirtual() {
        ContestParticipant real = makeParticipant(C1, ContestParticipantStatus.STARTED.wireValue(), false);
        ContestParticipant virt = makeParticipant(C2, ContestParticipantStatus.STARTED.wireValue(), true);
        when(participantMapper.findByUserId(USER_ID)).thenReturn(List.of(real, virt));
        when(contestMapper.selectBatchIds(any())).thenReturn(List.of(makeContest(C2)));
        when(contestProblemMapper.countByContestId(C2)).thenReturn(2L);

        List<ContestVO> result = projection.findUserContests(USER_ID, "virtual");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(C2);
    }

    @Test
    @DisplayName("filters by FINISHED or STARTED when type is anything else")
    void filtersFinishedOrStarted() {
        ContestParticipant finished = makeParticipant(C1, ContestParticipantStatus.FINISHED.wireValue(), false);
        ContestParticipant started = makeParticipant(C2, ContestParticipantStatus.STARTED.wireValue(), false);
        ContestParticipant registered = makeParticipant(C3, ContestParticipantStatus.REGISTERED.wireValue(), false);
        when(participantMapper.findByUserId(USER_ID)).thenReturn(List.of(finished, started, registered));
        when(contestMapper.selectBatchIds(any())).thenReturn(List.of(makeContest(C1), makeContest(C2)));
        when(contestProblemMapper.countByContestId(anyString())).thenReturn(1L);

        List<ContestVO> result = projection.findUserContests(USER_ID, "default");

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ContestVO::getId).containsExactlyInAnyOrder(C1, C2);
    }

    @Test
    @DisplayName("batches the contest read into exactly one selectBatchIds call regardless of N")
    void batchesContestRead() {
        List<ContestParticipant> many = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            many.add(makeParticipant("c" + i, ContestParticipantStatus.REGISTERED.wireValue(), false));
        }
        when(participantMapper.findByUserId(USER_ID)).thenReturn(many);
        when(contestMapper.selectBatchIds(any())).thenReturn(new ArrayList<>());

        projection.findUserContests(USER_ID, "registered");

        // Single batched read; no per-id selectById calls.
        verify(contestMapper, times(1)).selectBatchIds(any());
        verify(contestMapper, never()).selectById(anyString());
    }

    @Test
    @DisplayName("does not re-read the participant inside the VO projection")
    void noParticipantReRead() {
        ContestParticipant p = makeParticipant(C1, ContestParticipantStatus.REGISTERED.wireValue(), false);
        when(participantMapper.findByUserId(USER_ID)).thenReturn(List.of(p));
        when(contestMapper.selectBatchIds(any())).thenReturn(List.of(makeContest(C1)));
        when(contestProblemMapper.countByContestId(C1)).thenReturn(0L);

        projection.findUserContests(USER_ID, "registered");

        // Only the initial findByUserId is allowed; findByContestIdAndUserId
        // would be the re-read path. Before the seam, this happened N
        // times inside toContestVO. Pin the absence.
        verify(participantMapper, times(1)).findByUserId(anyString());
        verify(participantMapper, never()).findByContestIdAndUserId(anyString(), anyString());
    }
}
