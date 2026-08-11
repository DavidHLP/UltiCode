package com.ulticode.modules.contest.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * SQL seam for contest-owned relational cleanup.
 *
 * <p>The contest parent is soft-deleted, so application code must remove
 * contest-scoped rows in the same owner transaction. This mapper keeps the
 * unmodeled relation tables in one explicit seam without pretending that
 * global rankings or shared user/problem rows belong to the contest.</p>
 */
@Mapper
public interface ContestCascadeMapper {

    @Delete("DELETE FROM contest_adjudication_receipts "
            + "WHERE submission_id IN ("
            + "SELECT submission_id FROM contest_submissions WHERE contest_id = #{contestId})")
    int deleteAdjudicationReceiptsByContestId(@Param("contestId") String contestId);

    @Delete("DELETE FROM contest_problem_results WHERE contest_id = #{contestId}")
    int deleteProblemResultsByContestId(@Param("contestId") String contestId);

    @Delete("DELETE FROM contest_submissions WHERE contest_id = #{contestId}")
    int deleteSubmissionsByContestId(@Param("contestId") String contestId);

    @Delete("DELETE FROM first_solve_records WHERE contest_id = #{contestId}")
    int deleteFirstSolveRecordsByContestId(@Param("contestId") String contestId);

    @Delete("DELETE FROM contest_problems WHERE contest_id = #{contestId}")
    int deleteProblemsByContestId(@Param("contestId") String contestId);

    @Delete("DELETE FROM contest_analytics WHERE contest_id = #{contestId}")
    int deleteAnalyticsByContestId(@Param("contestId") String contestId);

    @Delete("DELETE FROM virtual_contest_sessions WHERE contest_id = #{contestId}")
    int deleteVirtualSessionsByContestId(@Param("contestId") String contestId);

    @Delete("DELETE FROM contest_announcements WHERE contest_id = #{contestId}")
    int deleteAnnouncementsByContestId(@Param("contestId") String contestId);

    @Delete("DELETE FROM contest_rankings WHERE contest_id = #{contestId}")
    int deleteRankingsByContestId(@Param("contestId") String contestId);

    @Delete("DELETE FROM contest_rating_calculations WHERE contest_id = #{contestId}")
    int deleteRatingCalculationsByContestId(@Param("contestId") String contestId);
}
