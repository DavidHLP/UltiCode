package com.ulticode.modules.admin.port.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.admin.port.AdminAnalyticsPort;
import com.ulticode.modules.admin.port.ContestSummary;
import com.ulticode.app.api.service.SubscriptionReadPort;
import com.ulticode.modules.admin.port.SubscriptionSummary;
import com.ulticode.app.api.dto.ContestAdminDTO;
import com.ulticode.app.api.service.ContestAdminReadPort;
import com.ulticode.app.api.service.ContestParticipantReadPort;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
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
    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;

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
        return submissionMapper.countDistinctUsersInRange(from, to);
    }

    @Override
    public long countSubmissionsInRange(LocalDateTime from) {
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Submission::getCreatedAt, from);
        return submissionMapper.selectCount(wrapper);
    }

    @Override
    public long countAcceptedSubmissionsInRange(LocalDateTime from) {
        LambdaQueryWrapper<Submission> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Submission::getCreatedAt, from).eq(Submission::getStatus, "Accepted");
        return submissionMapper.selectCount(wrapper);
    }

    @Override
    public long countContestsInRange(LocalDateTime from) {
        // AC #7: use read-port instead of Contest entity/LambdaQueryWrapper
        return contestAdminReadPort.selectByStartTimeAfter(from).size();
    }

    @Override
    public long countAllUsers() {
        return userMapper.selectCount(null);
    }
}