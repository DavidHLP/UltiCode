package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.ContestAdjudicationReceipt;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * Persistence seam for the contest adjudication idempotency fence.
 */
@Mapper
public interface ContestAdjudicationReceiptMapper extends BaseMapper<ContestAdjudicationReceipt> {

    /**
     * Insert the receipt only once. A duplicate generation is a successful
     * no-op for the caller and must not reopen scoring side effects.
     */
    @Insert("""
        INSERT IGNORE INTO contest_adjudication_receipts
          (id, submission_id, generation, verdict, is_accepted)
        VALUES
          (#{id}, #{submissionId}, #{generation}, #{verdict}, #{accepted})
        """)
    int insertIfAbsent(@Param("id") String id,
                       @Param("submissionId") String submissionId,
                       @Param("generation") long generation,
                       @Param("verdict") String verdict,
                       @Param("accepted") boolean accepted);

    /**
     * Count real contest submissions that still need a user-code verdict to be
     * adjudicated for the current submission generation. Infrastructure errors
     * are intentionally excluded because they do not change contest scoring.
     */
    @Select("SELECT COUNT(*) FROM contest_submissions cs "
            + "JOIN contest_participants cp ON cp.id = cs.participant_id "
            + "AND cp.contest_id = cs.contest_id AND cp.is_virtual = 0 "
            + "JOIN submissions s ON s.id = cs.submission_id "
            + "LEFT JOIN contest_adjudication_receipts r "
            + "ON r.submission_id = s.id AND r.generation = s.generation "
            + "WHERE cs.contest_id = #{contestId} AND r.id IS NULL "
            + "AND s.status IN ('Pending', 'Judging', 'Accepted', "
            + "'Presentation Error', 'Wrong Answer', 'Time Limit Exceeded', "
            + "'Memory Limit Exceeded', 'Output Limit Exceeded', 'Runtime Error', "
            + "'Compile Error')")
    long countUnadjudicatedRealSubmissions(@Param("contestId") String contestId);

    /**
     * Read the newest receipt while the caller holds the submission row lock.
     */
    @Select("SELECT generation FROM contest_adjudication_receipts "
            + "WHERE submission_id = #{submissionId} "
            + "ORDER BY generation DESC LIMIT 1 FOR UPDATE")
    Optional<Long> findMaxGenerationForSubmissionForUpdate(
            @Param("submissionId") String submissionId);
}
