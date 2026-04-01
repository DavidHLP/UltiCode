package com.ulticode.modules.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.submission.entity.Submission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * Mapper interface for Submission entity.
 * Extends MyBatis-Plus BaseMapper for basic CRUD operations.
 */
@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {

    /**
     * Find submissions by user ID with pagination.
     *
     * @param page   pagination object
     * @param userId user ID
     * @return paginated submissions
     */
    @Select("SELECT * FROM submissions WHERE user_id = #{userId} ORDER BY created_at DESC")
    IPage<Submission> findByUserId(Page<Submission> page, @Param("userId") String userId);

    /**
     * Find submissions by problem ID with pagination.
     *
     * @param page     pagination object
     * @param problemId problem ID
     * @param userId   user ID (optional filter)
     * @return paginated submissions
     */
    @Select("<script>" +
            "SELECT * FROM submissions WHERE problem_id = #{problemId} " +
            "<if test='userId != null'> AND user_id = #{userId}</if>" +
            " ORDER BY created_at DESC" +
            "</script>")
    IPage<Submission> findByProblemId(Page<Submission> page,
                                       @Param("problemId") Long problemId,
                                       @Param("userId") String userId);

    /**
     * Find the best (fastest accepted) submission for a problem by user.
     *
     * @param problemId problem ID
     * @param userId    user ID
     * @return the best submission if found
     */
    @Select("SELECT * FROM submissions WHERE problem_id = #{problemId} AND user_id = #{userId} " +
            "AND status = 'Accepted' ORDER BY runtime ASC, memory ASC, created_at DESC LIMIT 1")
    Optional<Submission> findBestByProblemIdAndUserId(@Param("problemId") Long problemId,
                                                       @Param("userId") String userId);

    /**
     * Find submissions by user ID and problem ID.
     *
     * @param userId    user ID
     * @param problemId problem ID
     * @return list of submissions
     */
    @Select("SELECT * FROM submissions WHERE user_id = #{userId} AND problem_id = #{problemId} " +
            "ORDER BY created_at DESC")
    List<Submission> findByUserIdAndProblemId(@Param("userId") String userId,
                                               @Param("problemId") Long problemId);

    /**
     * Count accepted submissions by user.
     *
     * @param userId user ID
     * @return count of accepted submissions
     */
    @Select("SELECT COUNT(DISTINCT problem_id) FROM submissions WHERE user_id = #{userId} AND status = 'Accepted'")
    Long countAcceptedProblemsByUserId(@Param("userId") String userId);

    /**
     * Count total submissions by user.
     *
     * @param userId user ID
     * @return count of total submissions
     */
    @Select("SELECT COUNT(*) FROM submissions WHERE user_id = #{userId}")
    Long countByUserId(@Param("userId") String userId);

    /**
     * Find distinct submission dates for a user in a given year.
     *
     * @param userId user ID
     * @param year   the year to filter by
     * @return list of date strings (YYYY-MM-DD format)
     */
    @Select("SELECT DISTINCT DATE_FORMAT(created_at, '%Y-%m-%d') as date FROM submissions " +
            "WHERE user_id = #{userId} AND YEAR(created_at) = #{year} ORDER BY date")
    List<String> findSubmissionDatesByYear(@Param("userId") String userId, @Param("year") Integer year);
}
