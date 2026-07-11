package com.ulticode.modules.admin.port.adapter;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.admin.port.AdminAnalyticsPort;
import com.ulticode.modules.admin.port.ContestSummary;
import com.ulticode.modules.admin.port.SubscriptionSummary;
import com.ulticode.modules.contest.entity.Contest;
import com.ulticode.modules.contest.entity.ContestParticipant;
import com.ulticode.modules.contest.mapper.ContestMapper;
import com.ulticode.modules.contest.mapper.ContestParticipantMapper;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.subscription.entity.Subscription;
import com.ulticode.modules.subscription.mapper.SubscriptionMapper;
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

    private final ContestMapper contestMapper;
    private final ContestParticipantMapper contestParticipantMapper;
    private final SubscriptionMapper subscriptionMapper;
    private final SubmissionMapper submissionMapper;
    private final UserMapper userMapper;

    @Override
    public ContestParticipationData loadContestData(LocalDateTime startDate) {
        LambdaQueryWrapper<Contest> contestWrapper = new LambdaQueryWrapper<>();
        contestWrapper.ge(Contest::getStartTime, startDate);
        List<Contest> contests = contestMapper.selectList(contestWrapper);

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
            // Short-circuit avoids IN () syntax error in MySQL.
            List<String> contestIds = contests.stream().map(Contest::getId).collect(Collectors.toList());
            for (ContestParticipant p : contestParticipantMapper.findByContestIds(contestIds)) {
                participantsByContest.merge(p.getContestId(), 1L, Long::sum);
                uniqueParticipants.add(p.getUserId());
            }
        }
        return new ContestParticipationData(contestSummaries, participantsByContest, uniqueParticipants);
    }

    @Override
    public long countActiveSubscriptions() {
        LambdaQueryWrapper<Subscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Subscription::getStatus, "ACTIVE");
        return subscriptionMapper.selectCount(wrapper);
    }

    @Override
    public List<SubscriptionSummary> listActiveSubscriptions() {
        LambdaQueryWrapper<Subscription> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Subscription::getStatus, "ACTIVE");
        return subscriptionMapper.selectList(wrapper).stream()
                .map(s -> new SubscriptionSummary(s.getPlan()))
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
        LambdaQueryWrapper<Contest> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Contest::getStartTime, from);
        return contestMapper.selectCount(wrapper);
    }

    @Override
    public long countAllUsers() {
        return userMapper.selectCount(null);
    }
}