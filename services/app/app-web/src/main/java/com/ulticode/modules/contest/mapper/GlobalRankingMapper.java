package com.ulticode.modules.contest.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.contest.entity.GlobalRanking;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * Global ranking mapper interface
 */
@Mapper
public interface GlobalRankingMapper extends BaseMapper<GlobalRanking> {

    /**
     * Find ranking by user ID
     */
    @Select("SELECT g.*, p.name FROM global_rankings g LEFT JOIN user_profiles p ON g.user_id = p.account_id WHERE g.user_id = #{userId} LIMIT 1")
    Optional<GlobalRanking> findByUserId(@Param("userId") String userId);

    /**
     * Batch-load and lock the global rankings used by one contest calculation.
     * User ids are sorted by the caller and the SQL repeats that order so two
     * contests sharing a user acquire overlapping row locks deterministically.
     */
    @Select("<script>SELECT g.* FROM global_rankings g " +
            "WHERE g.user_id IN " +
            "<foreach item='id' collection='userIds' open='(' separator=',' close=')'>" +
            "#{id}</foreach> ORDER BY g.user_id FOR UPDATE</script>")
    List<GlobalRanking> findByUserIdsForUpdate(@Param("userIds") List<String> userIds);

    /**
     * Find ranking by username
     */
    @Select("SELECT g.*, p.name FROM global_rankings g LEFT JOIN user_profiles p ON g.user_id = p.account_id WHERE g.username = #{username} LIMIT 1")
    Optional<GlobalRanking> findByUsername(@Param("username") String username);

    /**
     * Find top N rankings by global rank
     */
    @Select("SELECT g.*, p.name FROM global_rankings g LEFT JOIN user_profiles p ON g.user_id = p.account_id ORDER BY g.global_rank ASC LIMIT #{limit}")
    List<GlobalRanking> findTopRankings(@Param("limit") int limit);

    /**
     * Find rankings paginated by global rank (SQL-level pagination).
     */
    @Select("SELECT g.*, p.name FROM global_rankings g LEFT JOIN user_profiles p ON g.user_id = p.account_id ORDER BY g.global_rank ASC LIMIT #{limit} OFFSET #{offset}")
    List<GlobalRanking> findRankingsPaginated(
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * Find rankings by rating range
     */
    @Select("SELECT g.*, p.name FROM global_rankings g LEFT JOIN user_profiles p ON g.user_id = p.account_id WHERE g.rating BETWEEN #{minRating} AND #{maxRating} ORDER BY g.rating DESC")
    List<GlobalRanking> findByRatingRange(
            @Param("minRating") int minRating,
            @Param("maxRating") int maxRating
    );

    /**
     * Find rankings around a specific rank (for user context in leaderboard)
     */
    @Select("SELECT g.*, p.name FROM global_rankings g LEFT JOIN user_profiles p ON g.user_id = p.account_id WHERE g.global_rank BETWEEN #{startRank} AND #{endRank} ORDER BY g.global_rank ASC")
    List<GlobalRanking> findByRankRange(
            @Param("startRank") int startRank,
            @Param("endRank") int endRank
    );

    /**
     * Count total rankings
     */
    @Select("SELECT COUNT(*) FROM global_rankings")
    long countTotal();

    /**
     * Count rankings with rating >= threshold
     */
    @Select("SELECT COUNT(*) FROM global_rankings WHERE rating >= #{minRating}")
    long countByMinRating(@Param("minRating") int minRating);

    /**
     * Update user rating
     */
    @Update("UPDATE global_rankings SET rating = #{rating}, max_rating = GREATEST(max_rating, #{rating}), " +
            "rating_title = #{ratingTitle}, contests_attended = contests_attended + 1, " +
            "contests_rated = contests_rated + 1, last_contest_id = #{lastContestId}, " +
            "updated_at = NOW() WHERE user_id = #{userId}")
    int updateRating(
            @Param("userId") String userId,
            @Param("rating") int rating,
            @Param("ratingTitle") String ratingTitle,
            @Param("lastContestId") String lastContestId
    );

    /**
     * Update max rating title if new max achieved
     */
    @Update("UPDATE global_rankings SET max_rating_title = #{maxRatingTitle}, updated_at = NOW() " +
            "WHERE user_id = #{userId} AND rating >= max_rating")
    int updateMaxRatingTitle(
            @Param("userId") String userId,
            @Param("maxRatingTitle") String maxRatingTitle
    );

    /**
     * Recalculate global ranks (typically run after batch rating updates)
     * This uses a subquery to set rank based on rating ordering
     */
    @Update("UPDATE global_rankings gr JOIN (" +
            "SELECT user_id, RANK() OVER (ORDER BY rating DESC) as new_rank " +
            "FROM global_rankings" +
            ") r ON gr.user_id = r.user_id " +
            "SET gr.global_rank = r.new_rank, gr.updated_at = NOW()")
    int recalculateGlobalRanks();

    /**
     * Increment contests attended count
     */
    @Update("UPDATE global_rankings SET contests_attended = contests_attended + 1, updated_at = NOW() WHERE user_id = #{userId}")
    int incrementContestsAttended(@Param("userId") String userId);

    /**
     * Check if user has ranking record
     */
    @Select("SELECT COUNT(*) > 0 FROM global_rankings WHERE user_id = #{userId}")
    boolean existsByUserId(@Param("userId") String userId);

    /**
     * Find users by rating title
     */
    @Select("SELECT g.*, p.name FROM global_rankings g LEFT JOIN user_profiles p ON g.user_id = p.account_id WHERE g.rating_title = #{ratingTitle} ORDER BY g.rating DESC")
    List<GlobalRanking> findByRatingTitle(@Param("ratingTitle") String ratingTitle);

    /**
     * Find users by country
     */
    @Select("SELECT g.*, p.name FROM global_rankings g LEFT JOIN user_profiles p ON g.user_id = p.account_id WHERE g.country = #{country} ORDER BY g.global_rank ASC")
    List<GlobalRanking> findByCountry(@Param("country") String country);
}
