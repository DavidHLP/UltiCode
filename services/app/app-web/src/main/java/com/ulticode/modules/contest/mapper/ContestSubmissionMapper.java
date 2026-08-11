package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.ContestSubmission;
import com.ulticode.modules.submission.entity.Submission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus mapper for ContestSubmission entity.
 */
@Mapper
public interface ContestSubmissionMapper extends BaseMapper<ContestSubmission> {

    @Select("SELECT * FROM contest_submissions WHERE contest_id = #{contestId} AND participant_id = #{participantId} ORDER BY submitted_at ASC")
    List<ContestSubmission> findByContestIdAndParticipantId(
            @Param("contestId") String contestId,
            @Param("participantId") String participantId
    );

    @Select("SELECT COUNT(*) FROM contest_submissions WHERE contest_id = #{contestId}")
    long countByContestId(@Param("contestId") String contestId);

    @Select("SELECT COUNT(*) FROM contest_submissions cs "
            + "JOIN contests c ON c.id = cs.contest_id "
            + "WHERE c.is_visible = 1 AND c.is_deleted = 0")
    long countTotal();

    @Select("""
            SELECT s.*
            FROM contest_submissions cs
            JOIN submissions s ON s.id = cs.submission_id
            WHERE cs.contest_id = #{contestId}
              AND cs.contest_problem_id = #{contestProblemId}
              AND s.user_id = #{userId}
            ORDER BY cs.submitted_at DESC
            """)
    List<Submission> findSubmissionsByContestProblemAndUser(
            @Param("contestId") String contestId,
            @Param("contestProblemId") String contestProblemId,
            @Param("userId") String userId
    );

    /**
     * Reverse-lookup the contest_submission row for a given {@code submissions.id}.
     * Used by the durable contest consumer to apply the verdict after judge commit.
     *
     * @return Optional.empty() if the submission is not part of any contest
     */
    @Select("SELECT * FROM contest_submissions WHERE submission_id = #{submissionId} LIMIT 1")
    Optional<ContestSubmission> findBySubmissionId(@Param("submissionId") String submissionId);

    /**
     * Locate and lock the contest submission before applying a judge receipt.
     * The submission row serializes replays and different judge generations.
     */
    @Select("SELECT * FROM contest_submissions WHERE submission_id = #{submissionId} "
            + "ORDER BY submitted_at DESC LIMIT 1 FOR UPDATE")
    Optional<ContestSubmission> findBySubmissionIdForUpdate(
            @Param("submissionId") String submissionId);

    /**
     * Update the {@code is_accepted} flag of a contest_submission row. Idempotent.
     */
    @Update("UPDATE contest_submissions SET is_accepted = #{isAccepted} WHERE submission_id = #{submissionId}")
    int markAcceptedBySubmissionId(@Param("submissionId") String submissionId,
                                   @Param("isAccepted") boolean isAccepted);

    /**
     * Cascade-delete all contest_submission rows for a contest (used by deleteContestCascade).
     */
    @Delete("DELETE FROM contest_submissions WHERE contest_id = #{contestId}")
    int deleteByContestId(@Param("contestId") String contestId);
}
