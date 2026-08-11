package com.ulticode.modules.contest.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * Durable idempotency receipt for one contest rating calculation.
 *
 * <p>The unique contest key is claimed in the same transaction as rating
 * writes. A failed calculation rolls the claim back; a retry after commit is
 * a no-op.</p>
 */
@Mapper
public interface ContestRatingCalculationMapper {

    /**
     * Claim the rating calculation for a contest once.
     *
     * @return {@code 1} for the first claim, {@code 0} for a duplicate
     */
    @Insert("INSERT IGNORE INTO contest_rating_calculations "
            + "(id, contest_id) VALUES (#{id}, #{contestId})")
    int insertIfAbsent(@Param("id") String id, @Param("contestId") String contestId);
}
