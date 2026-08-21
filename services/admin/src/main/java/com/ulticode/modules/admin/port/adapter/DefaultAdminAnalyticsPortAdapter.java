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
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.rpc.RpcPolicy;
import jakarta.annotation.PreDestroy;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
public class DefaultAdminAnalyticsPortAdapter implements AdminAnalyticsPort {
    private final ContestAdminReadPort contestAdminReadPort;
    private final ContestParticipantReadPort contestParticipantReadPort;
    private final SubscriptionReadPort subscriptionReadPort;
    private final SubmissionAdminReadPort submissionAdminReadPort;
    private final CancellableQueryExecutor queryExecutor;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AccountQueryService accountQueryService;

    @Autowired
    public DefaultAdminAnalyticsPortAdapter(
            ContestAdminReadPort contestAdminReadPort,
            ContestParticipantReadPort contestParticipantReadPort,
            SubscriptionReadPort subscriptionReadPort,
            SubmissionAdminReadPort submissionAdminReadPort) {
        this(contestAdminReadPort, contestParticipantReadPort, subscriptionReadPort,
                submissionAdminReadPort, new CancellableQueryExecutor("admin-analytics-query", 6));
    }

    DefaultAdminAnalyticsPortAdapter(
            ContestAdminReadPort contestAdminReadPort,
            ContestParticipantReadPort contestParticipantReadPort,
            SubscriptionReadPort subscriptionReadPort,
            SubmissionAdminReadPort submissionAdminReadPort,
            CancellableQueryExecutor queryExecutor) {
        this.contestAdminReadPort = contestAdminReadPort;
        this.contestParticipantReadPort = contestParticipantReadPort;
        this.subscriptionReadPort = subscriptionReadPort;
        this.submissionAdminReadPort = submissionAdminReadPort;
        this.queryExecutor = queryExecutor;
    }

    @PreDestroy
    void shutdownQueryExecutor() {
        queryExecutor.close();
    }

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
    public List<SubscriptionSummary> listActiveSubscriptions() {
        return subscriptionReadPort.listActiveSubscriptionPlans().stream()
                .map(SubscriptionSummary::new)
                .collect(Collectors.toList());
    }

    private long countActiveSubscriptions() {
        return subscriptionReadPort.countActiveSubscriptions();
    }

    private long countDistinctSubmittersInRange(LocalDateTime from, LocalDateTime to) {
        return submissionAdminReadPort.countDistinctUsersInRange(from, to);
    }

    private long countSubmissionsInRange(LocalDateTime from) {
        return submissionAdminReadPort.countSubmissionsInRange(from);
    }

    private long countAcceptedSubmissionsInRange(LocalDateTime from) {
        return submissionAdminReadPort.countAcceptedSubmissionsInRange(from);
    }

    private long countContestsInRange(LocalDateTime from) {
        // AC #7: use read-port instead of Contest entity/LambdaQueryWrapper
        return contestAdminReadPort.selectByStartTimeAfter(from).size();
    }

    private long countAllUsers() {
        if (accountQueryService == null) {
            throw unavailable();
        }
        try {
            RpcResult<AuthAccountDTO> result = accountQueryService.queryAccounts(
                    new AccountQueryDTO(null, null, null, null, 1, 1, "joinedAt", "desc"));
            if (result == null || !result.success()
                    || result.page() == null || result.page().total() == null) {
                throw unavailable();
            }
            return result.page().total();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    @Override
    public AnalyticsOverviewData loadOverviewData(LocalDateTime from, LocalDateTime to) {
        CancellableQueryExecutor.Query<Long> totalUsers = queryExecutor.submit(this::countAllUsers);
        CancellableQueryExecutor.Query<Long> activeUsers = queryExecutor.submit(
                () -> countDistinctSubmittersInRange(from, to));
        CancellableQueryExecutor.Query<Long> submissions = queryExecutor.submit(
                () -> countSubmissionsInRange(from));
        CancellableQueryExecutor.Query<Long> accepted = queryExecutor.submit(
                () -> countAcceptedSubmissionsInRange(from));
        CancellableQueryExecutor.Query<Long> contests = queryExecutor.submit(
                () -> countContestsInRange(from));
        CancellableQueryExecutor.Query<Long> subscriptions = queryExecutor.submit(
                this::countActiveSubscriptions);
        CompletableFuture<?> all = CompletableFuture.allOf(
                totalUsers.result(), activeUsers.result(), submissions.result(), accepted.result(),
                contests.result(), subscriptions.result());
        try {
            all.get(RpcPolicy.QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            return new AnalyticsOverviewData(
                    totalUsers.result().join(),
                    activeUsers.result().join(),
                    submissions.result().join(),
                    accepted.result().join(),
                    contests.result().join(),
                    subscriptions.result().join());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            CancellableQueryExecutor.cancel(
                    totalUsers, activeUsers, submissions, accepted, contests, subscriptions);
            throw unavailable();
        } catch (ExecutionException | TimeoutException exception) {
            CancellableQueryExecutor.cancel(
                    totalUsers, activeUsers, submissions, accepted, contests, subscriptions);
            throw unavailable();
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                "Analytics owner query unavailable");
    }
}
