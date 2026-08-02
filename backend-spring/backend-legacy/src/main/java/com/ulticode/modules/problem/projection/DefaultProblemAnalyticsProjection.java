package com.ulticode.modules.problem.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.admin.dto.ProblemCompletionReportVO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.app.api.dto.ProblemDifficultyCompletion;
import com.ulticode.app.api.dto.ProblemTrend;
import com.ulticode.app.api.service.ProblemSubmissionStatsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link ProblemAnalyticsProjection}. Owns
 * the problem + tag + submission read joins that feed the admin completion
 * report.
 *
 * <p>Previous N+1 issues documented on the old
 * {@code AdminContentAnalyticsServiceImpl} are preserved as-is in the move:
 * the difficulty bucket and trending-problem reads are single aggregation
 * queries ({@code countProblemCompletionByDifficulty},
 * {@code findTrendingProblems}). The by-tag loop and hardest-problem scan
 * still carry the documented N+1 — the LIMIT 1000 cap on the outer tag
 * read prevents unbounded memory growth, and the admin facade is not on
 * a hot request path. Future batched versions belong here.
 *
 * @author ulticode
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultProblemAnalyticsProjection implements ProblemAnalyticsProjection {

    private final ProblemMapper problemMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final ProblemSubmissionStatsPort problemSubmissionStats;
    private final Clock clock;

    @Override
    public ProblemCompletionReportVO loadProblemCompletionReport(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now(clock).minusDays(daysToAnalyze);

        ProblemCompletionReportVO report = new ProblemCompletionReportVO();

        // Total attempts
        long totalAttempts = problemSubmissionStats.countCreatedSince(startDate);
        report.setTotalAttempts(totalAttempts);

        // Successful attempts (Accepted status)
        long successfulAttempts = problemSubmissionStats.countAcceptedSince(startDate);
        report.setSuccessfulAttempts(successfulAttempts);

        // Overall completion rate
        double overallRate = totalAttempts > 0 ? (successfulAttempts * 100.0 / totalAttempts) : 0.0;
        report.setOverallCompletionRate(overallRate);

        // By difficulty - single aggregation query replacing per-difficulty per-problem N+1 loop
        List<ProblemCompletionReportVO.DifficultyStats> byDifficulty = new ArrayList<>();
        List<ProblemDifficultyCompletion> diffStats = problemSubmissionStats.countProblemCompletionByDifficulty();
        Map<String, ProblemDifficultyCompletion> diffMap = diffStats.stream()
                .collect(Collectors.toMap(ProblemDifficultyCompletion::getDifficulty, row -> row));
        for (String difficulty : Arrays.asList("EASY", "MEDIUM", "HARD")) {
            ProblemDifficultyCompletion stats = diffMap.get(difficulty);
            int totalProblems = stats != null && stats.getTotalProblems() != null ? stats.getTotalProblems().intValue() : 0;
            int solvedProblems = stats != null && stats.getSolvedProblems() != null ? stats.getSolvedProblems().intValue() : 0;
            double rate = totalProblems > 0 ? (solvedProblems * 100.0 / totalProblems) : 0.0;
            byDifficulty.add(new ProblemCompletionReportVO.DifficultyStats(difficulty, totalProblems, solvedProblems, rate));
        }
        report.setByDifficulty(byDifficulty);

        // By tag (top 10)
        // NOTE: N+1 issue exists in the tag loop below (per-problem submission count queries).
        // The LIMIT 1000 caps the outer result set to prevent unbounded memory usage.
        // A future optimization should batch the per-problem queries into a single GROUP BY.
        List<ProblemTag> allTags = problemTagMapper.selectList(
                new LambdaQueryWrapper<ProblemTag>().last("LIMIT 1000"));
        List<ProblemCompletionReportVO.TagStats> byTag = allTags.stream()
                .limit(10)
                .map(tag -> {
                    List<ProblemTagRelation> relations = problemTagRelationMapper.selectList(
                            new LambdaQueryWrapper<ProblemTagRelation>()
                                    .eq(ProblemTagRelation::getTagId, tag.getId())
                    );

                    int totalProblems = relations.size();
                    int solvedProblems = 0;

                    for (ProblemTagRelation relation : relations) {
                        if (problemSubmissionStats.countAcceptedByProblemId(relation.getProblemId()) > 0) {
                            solvedProblems++;
                        }
                    }

                    double rate = totalProblems > 0 ? (solvedProblems * 100.0 / totalProblems) : 0.0;
                    return new ProblemCompletionReportVO.TagStats(tag.getId(), tag.getLabel(), totalProblems, solvedProblems, rate);
                })
                .sorted((a, b) -> Double.compare(b.getRate(), a.getRate()))
                .collect(Collectors.toList());
        report.setByTag(byTag);

        // Trending problems - single aggregation query replacing load-all + Java groupBy + N lookups
        List<ProblemCompletionReportVO.TrendingProblem> trendingProblems = new ArrayList<>();
        List<ProblemTrend> trendingData = problemSubmissionStats.findTrendingProblems(startDate, 10);
        for (ProblemTrend row : trendingData) {
            long problemId = row.getProblemId();
            int attemptCount = row.getAttemptCount() != null ? row.getAttemptCount().intValue() : 0;
            int acceptedCount = row.getAcceptedCount() != null ? row.getAcceptedCount().intValue() : 0;
            double rate = attemptCount > 0 ? (acceptedCount * 100.0 / attemptCount) : 0.0;
            Problem problem = problemMapper.selectById(problemId);
            trendingProblems.add(new ProblemCompletionReportVO.TrendingProblem(
                    String.valueOf(problemId),
                    problem != null ? problem.getTitle() : "Problem " + problemId,
                    attemptCount,
                    rate
            ));
        }
        report.setTrendingProblems(trendingProblems);

        // Hardest problems (lowest completion rate)
        List<Problem> publishedProblems = problemMapper.selectList(
                new LambdaQueryWrapper<Problem>().eq(Problem::getStatus, "PUBLISHED")
        );

        List<ProblemCompletionReportVO.HardestProblem> hardestProblems = publishedProblems.stream()
                .limit(10)
                .map(problem -> {
                    long attemptsForProblem = problemSubmissionStats.countByProblemId(problem.getId());
                    long acceptedCount = problemSubmissionStats.countAcceptedByProblemId(problem.getId());
                    double rate = attemptsForProblem > 0 ? (acceptedCount * 100.0 / attemptsForProblem) : 0.0;
                    return new ProblemCompletionReportVO.HardestProblem(
                            problem.getId().toString(),
                            problem.getTitle(),
                            problem.getDifficulty(),
                            rate
                    );
                })
                .sorted((a, b) -> Double.compare(a.getCompletionRate(), b.getCompletionRate()))
                .collect(Collectors.toList());
        report.setHardestProblems(hardestProblems);

        return report;
    }
}
