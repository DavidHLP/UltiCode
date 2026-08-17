package com.ulticode.modules.admin.port.adapter;

import com.ulticode.modules.admin.port.AdminAnalyticsPort;
import com.ulticode.modules.admin.port.ContestSummary;
import com.ulticode.app.api.service.SubscriptionReadPort;
import com.ulticode.modules.admin.port.SubscriptionSummary;
import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.app.api.service.ContestParticipantReadPort;
import com.ulticode.submission.api.service.SubmissionAdminReadPort;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.rpc.RpcResult;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Production adapter for {@link AdminAnalyticsPort}. Concentrates the
 * five cross-module mapper reads that
 * {@code AdminAnalyticsServiceImpl} previously inlined.
 *
 * <p>Each method owns one mapper call (or one small batch). The
 * admin module no longer imports
 * {@code ContestMapper / ContestParticipantMapper / SubscriptionMapper /
 * SubmissionMapper / UserMapper} — all five cross-module dependencies
 * are hidden behind this adapter.
 *
 * <p>The {@code Contest} and {@code Subscription} entities are mapped to
 * the admin-owned {@link ContestSummary} and {@link SubscriptionSummary}
 * projection records on this side of the seam, so admin code never
 * touches a foreign entity.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class DefaultAdminAnalyticsPortAdapter implements AdminAnalyticsPort {

    private final ContestAdminReadPort contestAdminReadPort;
    private final ContestParticipantReadPort contestParticipantReadPort;
    private final SubscriptionReadPort subscriptionReadPort;
    private final SubmissionAdminReadPort submissionAdminReadPort;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private AccountQueryService accountQueryService;

    @Override
    public ContestParticipationData loadContestData(LocalDateTime startDate) {
        List<ContestAdminDTO> contests = contestAdminReadPort.selectByStartTimeAfter(startDate);

        List<ContestSummary> contestSummaries = contests.stream()
                .map(c -> new ContestSummary(
                        c.getId(),
                        c.getTitle(),
                        c.getContestType(),
                        c.getStartTime()))
                .collect(Collectors.toList());

        Map<String, Long> participantsByContest = new HashMap<>();
        Set<String> uniqueParticipants = new HashSet<>();
        if (!contests.isEmpty()) {
            List<String> contestIds = contests.stream().map(ContestAdminDTO::getId).collect(Collectors.toList());
            for (ContestParticipantReadPort.ParticipantInfo p : contestParticipantReadPort.findByContestIds(contestIds)) {
                participantsByContest.merge(p.contestId(), 1L, Long::sum);
                uniqueParticipants.add(p.userId());
            }
        }
        return new ContestParticipationData(contestSummaries, participantsByContest, uniqueParticipants);
    }

    @Override
    public long countActiveSubscriptions() {
        return subscriptionReadPort.countActiveSubscriptions();
    }

    @Override
    public List<SubscriptionSummary> listActiveSubscriptions() {
        return subscriptionReadPort.listActiveSubscriptionPlans().stream()
                .map(SubscriptionSummary::new)
                .collect(Collectors.toList());
    }

    @Override
    public long countDistinctSubmittersInRange(LocalDateTime from, LocalDateTime to) {
        return submissionAdminReadPort.countDistinctUsersInRange(from, to);
    }

    @Override
    public long countSubmissionsInRange(LocalDateTime from) {
        return submissionAdminReadPort.countSubmissionsInRange(from);
    }

    @Override
    public long countAcceptedSubmissionsInRange(LocalDateTime from) {
        return submissionAdminReadPort.countAcceptedSubmissionsInRange(from);
    }

    @Override
    public long countContestsInRange(LocalDateTime from) {
        // AC #7: use read-port instead of Contest entity/LambdaQueryWrapper
        return contestAdminReadPort.selectByStartTimeAfter(from).size();
    }

    @Override
    public long countAllUsers() {
        if (accountQueryService == null) {
            return 0L;
        }
        RpcResult<AuthAccountDTO> result = accountQueryService.queryAccounts(
                new AccountQueryDTO(null, null, null, null, 1, 1, "joinedAt", "desc"));
        if (result == null || result.page() == null || result.page().total() == null) {
            return 0L;
        }
        return result.page().total();
    }
}
