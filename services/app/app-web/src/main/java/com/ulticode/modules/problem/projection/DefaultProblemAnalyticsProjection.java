package com.ulticode.modules.problem.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.app.api.dto.ProblemCompletionReportDTO;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.submission.api.dto.ProblemDifficultyCompletion;
import com.ulticode.submission.api.dto.ProblemTrend;
import com.ulticode.app.api.service.ProblemAnalyticsReadPort;
import com.ulticode.submission.api.service.ProblemSubmissionStatsPort;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Default (and only) provider for {@link ProblemAnalyticsReadPort}. Owns
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
@Component
@Primary
@RequiredArgsConstructor
public class DefaultProblemAnalyticsProjection implements ProblemAnalyticsReadPort {

    private final ProblemMapper problemMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final ProblemSubmissionStatsPort problemSubmissionStats;
    private final Clock clock;

    @Override
    public ProblemCompletionReportDTO loadProblemCompletionReport(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now(clock).minusDays(daysToAnalyze);

        long totalAttempts = problemSubmissionStats.countCreatedSince(startDate);
        long successfulAttempts = problemSubmissionStats.countAcceptedSince(startDate);
        double overallRate = totalAttempts > 0 ? successfulAttempts * 100.0 / totalAttempts : 0.0;

        List<ProblemCompletionReportDTO.DifficultyStats> byDifficulty = new ArrayList<>();
        List<ProblemDifficultyCompletion> diffStats = problemSubmissionStats.countProblemCompletionByDifficulty();
        Map<String, ProblemDifficultyCompletion> diffMap = (diffStats == null ? List.<ProblemDifficultyCompletion>of() : diffStats)
                .stream()
                .collect(Collectors.toMap(ProblemDifficultyCompletion::getDifficulty, row -> row));
        for (String difficulty : Arrays.asList("EASY", "MEDIUM", "HARD")) {
            ProblemDifficultyCompletion stats = diffMap.get(difficulty);
            int totalProblems = stats != null && stats.getTotalProblems() != null
                    ? stats.getTotalProblems().intValue() : 0;
            int solvedProblems = stats != null && stats.getSolvedProblems() != null
                    ? stats.getSolvedProblems().intValue() : 0;
            double rate = totalProblems > 0 ? solvedProblems * 100.0 / totalProblems : 0.0;
            byDifficulty.add(new ProblemCompletionReportDTO.DifficultyStats(
                    difficulty, totalProblems, solvedProblems, rate));
        }

        List<ProblemTag> allTags = problemTagMapper.selectList(
                new LambdaQueryWrapper<ProblemTag>().last("LIMIT 1000"));
        List<ProblemCompletionReportDTO.TagStats> byTag = (allTags == null ? List.<ProblemTag>of() : allTags)
                .stream()
                .limit(10)
                .map(tag -> {
                    List<ProblemTagRelation> relations = problemTagRelationMapper.selectList(
                            new LambdaQueryWrapper<ProblemTagRelation>()
                                    .eq(ProblemTagRelation::getTagId, tag.getId()));
                    List<ProblemTagRelation> safeRelations = relations == null ? List.of() : relations;
                    int solvedProblems = 0;
                    for (ProblemTagRelation relation : safeRelations) {
                        if (problemSubmissionStats.countAcceptedByProblemId(relation.getProblemId()) > 0) {
                            solvedProblems++;
                        }
                    }
                    int totalProblems = safeRelations.size();
                    double rate = totalProblems > 0 ? solvedProblems * 100.0 / totalProblems : 0.0;
                    return new ProblemCompletionReportDTO.TagStats(
                            tag.getId(), tag.getLabel(), totalProblems, solvedProblems, rate);
                })
                .sorted((a, b) -> Double.compare(b.rate(), a.rate()))
                .collect(Collectors.toList());

        List<ProblemCompletionReportDTO.TrendingProblem> trendingProblems = new ArrayList<>();
        List<ProblemTrend> trendingData = problemSubmissionStats.findTrendingProblems(startDate, 10);
        for (ProblemTrend row : trendingData == null ? List.<ProblemTrend>of() : trendingData) {
            long problemId = row.getProblemId();
            int attemptCount = row.getAttemptCount() != null ? row.getAttemptCount().intValue() : 0;
            int acceptedCount = row.getAcceptedCount() != null ? row.getAcceptedCount().intValue() : 0;
            double rate = attemptCount > 0 ? acceptedCount * 100.0 / attemptCount : 0.0;
            Problem problem = problemMapper.selectById(problemId);
            trendingProblems.add(new ProblemCompletionReportDTO.TrendingProblem(
                    String.valueOf(problemId),
                    problem != null ? problem.getTitle() : "Problem " + problemId,
                    attemptCount,
                    rate));
        }

        List<Problem> publishedProblems = problemMapper.selectList(
                new LambdaQueryWrapper<Problem>().eq(Problem::getStatus, "PUBLISHED"));
        List<ProblemCompletionReportDTO.HardestProblem> hardestProblems =
                (publishedProblems == null ? List.<Problem>of() : publishedProblems)
                        .stream()
                        .limit(10)
                        .map(problem -> {
                            long attemptsForProblem = problemSubmissionStats.countByProblemId(problem.getId());
                            long acceptedCount = problemSubmissionStats.countAcceptedByProblemId(problem.getId());
                            double rate = attemptsForProblem > 0
                                    ? acceptedCount * 100.0 / attemptsForProblem : 0.0;
                            return new ProblemCompletionReportDTO.HardestProblem(
                                    problem.getId().toString(),
                                    problem.getTitle(),
                                    problem.getDifficulty(),
                                    rate);
                        })
                        .sorted((a, b) -> Double.compare(a.completionRate(), b.completionRate()))
                        .collect(Collectors.toList());

        return new ProblemCompletionReportDTO(
                totalAttempts,
                successfulAttempts,
                overallRate,
                byDifficulty,
                byTag,
                trendingProblems,
                hardestProblems);
    }
}
