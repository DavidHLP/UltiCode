package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.admin.dto.ProblemCompletionReportVO;
import com.ulticode.modules.admin.service.AdminContentAnalyticsService;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.problem.entity.ProblemTag;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import com.ulticode.modules.problem.mapper.ProblemMapper;
import com.ulticode.modules.problem.mapper.ProblemTagMapper;
import com.ulticode.modules.problem.mapper.ProblemTagRelationMapper;
import com.ulticode.modules.submission.entity.Submission;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementation of AdminContentAnalyticsService.
 * Handles problem completion statistics and content engagement analytics.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminContentAnalyticsServiceImpl implements AdminContentAnalyticsService {

    private final ProblemMapper problemMapper;
    private final ProblemTagMapper problemTagMapper;
    private final ProblemTagRelationMapper problemTagRelationMapper;
    private final SubmissionMapper submissionMapper;
    private final Clock clock;

    @Override
    public ProblemCompletionReportVO getProblemCompletionReport(Integer days) {
        int daysToAnalyze = days != null && days > 0 ? days : 30;
        LocalDateTime startDate = LocalDateTime.now(clock).minusDays(daysToAnalyze);

        ProblemCompletionReportVO report = new ProblemCompletionReportVO();

        // Total attempts
        LambdaQueryWrapper<Submission> allWrapper = new LambdaQueryWrapper<>();
        allWrapper.ge(Submission::getCreatedAt, startDate);
        long totalAttempts = submissionMapper.selectCount(allWrapper);
        report.setTotalAttempts(totalAttempts);

        // Successful attempts (Accepted status)
        LambdaQueryWrapper<Submission> acceptedWrapper = new LambdaQueryWrapper<>();
        acceptedWrapper.ge(Submission::getCreatedAt, startDate)
                .eq(Submission::getStatus, "Accepted");
        long successfulAttempts = submissionMapper.selectCount(acceptedWrapper);
        report.setSuccessfulAttempts(successfulAttempts);

        // Overall completion rate
        double overallRate = totalAttempts > 0 ? (successfulAttempts * 100.0 / totalAttempts) : 0.0;
        report.setOverallCompletionRate(overallRate);

        // By difficulty - single aggregation query replacing per-difficulty per-problem N+1 loop
        List<ProblemCompletionReportVO.DifficultyStats> byDifficulty = new ArrayList<>();
        List<Map<String, Object>> diffStats = submissionMapper.countProblemCompletionByDifficulty();
        Map<String, Map<String, Object>> diffMap = diffStats.stream()
                .collect(Collectors.toMap(row -> row.get("difficulty").toString(), row -> row));
        for (String difficulty : Arrays.asList("EASY", "MEDIUM", "HARD")) {
            Map<String, Object> stats = diffMap.get(difficulty);
            int totalProblems = stats != null ? ((Number) stats.get("total_problems")).intValue() : 0;
            int solvedProblems = stats != null ? ((Number) stats.get("solved_problems")).intValue() : 0;
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
                        LambdaQueryWrapper<Submission> subWrapper = new LambdaQueryWrapper<>();
                        subWrapper.eq(Submission::getProblemId, relation.getProblemId())
                                .eq(Submission::getStatus, "Accepted");
                        if (submissionMapper.selectCount(subWrapper) > 0) {
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
        List<Map<String, Object>> trendingData = submissionMapper.findTrendingProblems(startDate, 10);
        for (Map<String, Object> row : trendingData) {
            long problemId = ((Number) row.get("problem_id")).longValue();
            int attemptCount = ((Number) row.get("attempt_count")).intValue();
            int acceptedCount = ((Number) row.get("accepted_count")).intValue();
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
                    long attemptsForProblem = submissionMapper.selectCount(
                            new LambdaQueryWrapper<Submission>()
                                    .eq(Submission::getProblemId, problem.getId())
                    );
                    long acceptedCount = submissionMapper.selectCount(
                            new LambdaQueryWrapper<Submission>()
                                    .eq(Submission::getProblemId, problem.getId())
                                    .eq(Submission::getStatus, "Accepted")
                    );
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
