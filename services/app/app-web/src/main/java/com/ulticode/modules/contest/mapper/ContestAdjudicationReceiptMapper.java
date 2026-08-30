package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.ContestAdjudicationReceipt;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
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

    /** Find real contest submission ids; status/generation belong to Submission. */
    @Select("SELECT cs.submission_id FROM contest_submissions cs "
            + "JOIN contest_participants cp ON cp.id = cs.participant_id "
            + "AND cp.contest_id = cs.contest_id "
            + "WHERE cs.contest_id = #{contestId} AND cp.is_virtual = 0")
    List<String> findRealSubmissionIdsByContestId(@Param("contestId") String contestId);

    /** Read local adjudication generations for a bounded set of submission ids. */
    @ConstructorArgs({
            @Arg(column = "submission_id", javaType = String.class),
            @Arg(column = "generation", javaType = Long.class)
    })
    @Select("<script>SELECT submission_id, generation FROM contest_adjudication_receipts "
            + "WHERE submission_id IN "
            + "<foreach collection='submissionIds' item='id' open='(' separator=',' close=')'>"
            + "#{id}</foreach></script>")
    List<ReceiptGeneration> findReceiptGenerationsBySubmissionIds(
            @Param("submissionIds") Collection<String> submissionIds);

    record ReceiptGeneration(String submissionId, Long generation) {
    }

    /**
     * Read the newest local receipt generation for the adjudication fence.
     */
    @Select("SELECT generation FROM contest_adjudication_receipts "
            + "WHERE submission_id = #{submissionId} "
            + "ORDER BY generation DESC LIMIT 1 FOR UPDATE")
    Optional<Long> findMaxGenerationForSubmissionForUpdate(
            @Param("submissionId") String submissionId);
}
