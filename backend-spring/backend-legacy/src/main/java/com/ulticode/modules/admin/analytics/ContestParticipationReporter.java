package com.ulticode.modules.admin.analytics;

import com.ulticode.modules.admin.dto.ContestParticipationReportVO;
import com.ulticode.modules.admin.port.AdminAnalyticsPort;
import com.ulticode.modules.admin.port.ContestSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Builds the {@link ContestParticipationReportVO} dashboard from a single
 * {@link AdminAnalyticsPort#loadContestData} batch.
 *
 * <p>Owns the contest-participation math that was previously inlined in
 * {@code AdminAnalyticsServiceImpl}: type-bucketed averages, top-N
 * contests by participant count, and weekly trend rows. Pure math —
 * does not touch any mapper or foreign entity.
 *
 * <p>The number of top contests and the number of trend weeks are
 * derived from the input ({@code limit} and {@code daysToAnalyze / 7});
 * see {@link #buildTopContests(List, Map, int)} and
 * {@link #buildParticipationTrend(List, Map, int, Clock)} for the rules.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class ContestParticipationReporter {

    /**
     * Maximum number of top contests returned in the report. Matches the
     * historical hard-coded value in the inline implementation.
     */
    static final int TOP_CONTESTS_LIMIT = 10;

    /**
     * Default completion-rate value carried on each top-contest row.
     * (The dashboard does not currently have a real per-contest completion
     * source; the inline implementation hard-coded {@code 100.0} — kept
     * as a named constant here.)
     */
    static final double DEFAULT_TOP_CONTEST_COMPLETION_RATE = 100.0;

    private final AdminAnalyticsPort adminAnalyticsPort;
    private final Clock clock;

    /**
     * Build the full contest participation report for the analysis
     * window starting {@code daysToAnalyze} days before "now".
     *
     * @param daysToAnalyze window length in days; {@code null} or non-positive
     *                      values fall back to {@link #DEFAULT_DAYS}
     * @return assembled report VO
     */
    public ContestParticipationReportVO buildReport(Integer daysToAnalyze) {
        int resolvedDays = daysToAnalyze != null && daysToAnalyze > 0 ? daysToAnalyze : DEFAULT_DAYS;
        LocalDateTime startDate = LocalDateTime.now(clock).minusDays(resolvedDays);

        AdminAnalyticsPort.ContestParticipationData data = adminAnalyticsPort.loadContestData(startDate);
        List<ContestSummary> contests = data.contests();
        Map<String, Long> participantsByContest = data.participantsByContest();
        int uniqueParticipantCount = data.uniqueParticipants().size();

        ContestParticipationReportVO report = new ContestParticipationReportVO();
        int totalContestsCount = contests.size();
        report.setTotalContests(totalContestsCount);
        report.setTotalParticipants((long) uniqueParticipantCount);
        report.setAverageParticipantsPerContest(
                totalContestsCount > 0 ? (double) uniqueParticipantCount / totalContestsCount : 0.0);
        report.setByType(buildTypeStats(contests, participantsByContest));
        report.setTopContests(buildTopContests(contests, participantsByContest, TOP_CONTESTS_LIMIT));
        report.setVirtualParticipation(new ContestParticipationReportVO.VirtualParticipation(0, 0.0));
        report.setParticipationTrend(buildParticipationTrend(contests, participantsByContest, resolvedDays, clock));

        return report;
    }

    /**
     * Default analysis window when {@code daysToAnalyze} is null or
     * non-positive. Matches the historical inline fallback.
     */
    static final int DEFAULT_DAYS = 30;

    /**
     * Group contests by {@code contestType} and compute the running average
     * participants per contest in each type bucket.
     */
    private List<ContestParticipationReportVO.TypeStats> buildTypeStats(
            List<ContestSummary> contests, Map<String, Long> participantsByContest) {
        Map<String, ContestParticipationReportVO.TypeStats> typeStatsMap = new HashMap<>();
        for (ContestSummary contest : contests) {
            long participantCount = participantsByContest.getOrDefault(contest.id(), 0L);
            typeStatsMap.merge(contest.contestType(),
                    new ContestParticipationReportVO.TypeStats(contest.contestType(), 1, (double) participantCount),
                    (existing, newValue) -> new ContestParticipationReportVO.TypeStats(
                            contest.contestType(),
                            existing.getCount() + 1,
                            (existing.getAvgParticipants() * existing.getCount() + participantCount) / (existing.getCount() + 1)
                    ));
        }
        return new ArrayList<>(typeStatsMap.values());
    }

    /**
     * Build the top-N contests by participant count.
     */
    private List<ContestParticipationReportVO.TopContest> buildTopContests(
            List<ContestSummary> contests, Map<String, Long> participantsByContest, int limit) {
        return contests.stream()
                .map(contest -> new ContestParticipationReportVO.TopContest(
                        contest.id(),
                        contest.title(),
                        participantsByContest.getOrDefault(contest.id(), 0L).intValue(),
                        DEFAULT_TOP_CONTEST_COMPLETION_RATE
                ))
                .sorted((a, b) -> Integer.compare(b.getParticipants(), a.getParticipants()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Bucket the already-loaded contests into weeks (oldest first) and
     * produce one trend row per week. The {@code participants} field is
     * the sum of per-contest participation counts within the week — see
     * {@link ContestParticipationReportVO.ParticipationTrend} for why
     * this is an approximation rather than a distinct-user count.
     */
    private List<ContestParticipationReportVO.ParticipationTrend> buildParticipationTrend(
            List<ContestSummary> contests, Map<String, Long> participantsByContest, int daysToAnalyze, Clock trendClock) {
        List<ContestParticipationReportVO.ParticipationTrend> trend = new ArrayList<>();
        for (int i = (daysToAnalyze / 7); i >= 0; i--) {
            LocalDateTime weekStart = LocalDateTime.now(trendClock).minusWeeks(i).withHour(0).withMinute(0).withSecond(0);
            LocalDateTime weekEnd = weekStart.plusWeeks(1);

            List<ContestSummary> weekContests = contests.stream()
                    .filter(c -> !c.startTime().isBefore(weekStart) && c.startTime().isBefore(weekEnd))
                    .collect(Collectors.toList());

            long weekParticipants = weekContests.stream()
                    .mapToLong(c -> participantsByContest.getOrDefault(c.id(), 0L))
                    .sum();

            trend.add(new ContestParticipationReportVO.ParticipationTrend(
                    weekStart.toLocalDate().toString(),
                    weekContests.size(),
                    (int) weekParticipants
            ));
        }
        return trend;
    }
}