package com.ulticode.modules.admin.port.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.app.api.service.ContestParticipantReadPort;
import com.ulticode.app.api.service.SubscriptionReadPort;
import com.ulticode.modules.admin.port.AdminAnalyticsPort;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultAdminAnalyticsPortAdapterTest {

    @Mock
    private ContestAdminReadPort contestAdminReadPort;
    @Mock
    private ContestParticipantReadPort contestParticipantReadPort;
    @Mock
    private SubscriptionReadPort subscriptionReadPort;
    @Mock
    private SubmissionAdminReadPort submissionAdminReadPort;
    @Mock
    private AccountQueryService accountQueryService;

    @Test
    void loadContestDataUsesOneParticipantBatchForTheWholeContestPage() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 1, 0, 0);
        ContestAdminDTO first = contest("contest-1", "First");
        ContestAdminDTO second = contest("contest-2", "Second");
        when(contestAdminReadPort.selectByStartTimeAfter(start)).thenReturn(List.of(first, second));
        when(contestParticipantReadPort.findByContestIds(List.of("contest-1", "contest-2")))
                .thenReturn(List.of(
                        new ContestParticipantReadPort.ParticipantInfo("contest-1", "user-1"),
                        new ContestParticipantReadPort.ParticipantInfo("contest-1", "user-2"),
                        new ContestParticipantReadPort.ParticipantInfo("contest-2", "user-1")));

        AdminAnalyticsPort.ContestParticipationData data = adapter().loadContestData(start);

        assertThat(data.contests()).extracting("id")
                .containsExactly("contest-1", "contest-2");
        assertThat(data.participantsByContest())
                .containsEntry("contest-1", 2L)
                .containsEntry("contest-2", 1L);
        assertThat(data.uniqueParticipants()).containsExactlyInAnyOrder("user-1", "user-2");
        verify(contestParticipantReadPort, times(1)).findByContestIds(List.of("contest-1", "contest-2"));
    }

    @Test
    void loadOverviewDataKeepsTheCoarseSeamToOneCallPerAggregate() {
        LocalDateTime from = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 8, 1, 0, 0);
        when(contestAdminReadPort.selectByStartTimeAfter(from)).thenReturn(List.of(contest("contest-1", "First")));
        when(subscriptionReadPort.countActiveSubscriptions()).thenReturn(7L);
        when(submissionAdminReadPort.countDistinctUsersInRange(from, to)).thenReturn(30L);
        when(submissionAdminReadPort.countSubmissionsInRange(from)).thenReturn(200L);
        when(submissionAdminReadPort.countAcceptedSubmissionsInRange(from)).thenReturn(50L);

        AdminAnalyticsPort.AnalyticsOverviewData overview = adapter().loadOverviewData(from, to);

        assertThat(overview).isEqualTo(new AdminAnalyticsPort.AnalyticsOverviewData(0L, 30L, 200L, 50L, 1L, 7L));
        verify(contestAdminReadPort, times(1)).selectByStartTimeAfter(from);
        verify(subscriptionReadPort, times(1)).countActiveSubscriptions();
        verify(submissionAdminReadPort, times(1)).countDistinctUsersInRange(from, to);
        verify(submissionAdminReadPort, times(1)).countSubmissionsInRange(from);
        verify(submissionAdminReadPort, times(1)).countAcceptedSubmissionsInRange(from);
        verify(accountQueryService, times(1)).queryAccounts(any());
    }

    private DefaultAdminAnalyticsPortAdapter adapter() {
        DefaultAdminAnalyticsPortAdapter adapter = new DefaultAdminAnalyticsPortAdapter(
                contestAdminReadPort,
                contestParticipantReadPort,
                subscriptionReadPort,
                submissionAdminReadPort);
        ReflectionTestUtils.setField(adapter, "accountQueryService", accountQueryService);
        return adapter;
    }

    private static ContestAdminDTO contest(String id, String title) {
        ContestAdminDTO contest = new ContestAdminDTO();
        contest.setId(id);
        contest.setTitle(title);
        contest.setContestType("ACM");
        contest.setStartTime(LocalDateTime.of(2026, 7, 2, 0, 0));
        return contest;
    }
}
