package com.ulticode.modules.dashboard.mapper;

import com.ulticode.common.dto.DashboardBucketCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * App-owner persistence seam for the Admin Dashboard aggregate provider.
 * Every table referenced here is App-owned.
 */
@Mapper
public interface DashboardAdminMapper {

    @Select("SELECT COUNT(*) FROM problems")
    Long countTotalProblems();

    @Select("SELECT COUNT(*) FROM problems WHERE is_active = 1 AND is_deleted = 0")
    Long countPublishedProblems();

    @Select("SELECT difficulty AS bucket, COUNT(*) AS count FROM problems GROUP BY difficulty")
    List<DashboardBucketCount> countProblemsByDifficulty();

    @Select("""
            SELECT CASE
                       WHEN is_deleted = 1 THEN 'DELETED'
                       WHEN is_active = 1 THEN 'ACTIVE'
                       ELSE 'INACTIVE'
                   END AS bucket,
                   COUNT(*) AS count
            FROM problems
            GROUP BY is_deleted, is_active
            """)
    List<DashboardBucketCount> countProblemsByStatus();

    @Select("SELECT COUNT(*) FROM contests")
    Long countTotalContests();

    @Select("SELECT COUNT(*) FROM contests WHERE start_time > #{now}")
    Long countUpcomingContests(@Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM contests WHERE start_time <= #{now} AND end_time > #{now}")
    Long countRunningContests(@Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM contests WHERE end_time <= #{now}")
    Long countFinishedContests(@Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM solutions")
    Long countTotalSolutions();

    // The canonical App solution table has no publication flag; every stored
    // solution is part of the owner read shape until a visibility seam exists.
    @Select("SELECT COUNT(*) FROM solutions")
    Long countPublishedSolutions();

    @Select("SELECT 0")
    Long countFlaggedSolutions();

    @Select("SELECT COUNT(*) FROM forum_posts")
    Long countForumPosts();

    @Select("SELECT 0")
    Long countForumComments();

    @Select("SELECT 0")
    Long countForumCommunities();

    @Select("SELECT 0")
    Long countFlaggedForumPosts();

    @Select("SELECT 0")
    Long countFlaggedForumComments();

    @Select("""
            SELECT DATE_FORMAT(created_at, #{dateFormat}) AS bucket, COUNT(*) AS count
            FROM problems
            WHERE created_at >= #{start} AND created_at <= #{end}
            GROUP BY DATE_FORMAT(created_at, #{dateFormat})
            ORDER BY bucket
            """)
    List<DashboardBucketCount> chartProblems(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("dateFormat") String dateFormat);

    @Select("""
            SELECT DATE_FORMAT(created_at, #{dateFormat}) AS bucket, COUNT(*) AS count
            FROM contests
            WHERE created_at >= #{start} AND created_at <= #{end}
            GROUP BY DATE_FORMAT(created_at, #{dateFormat})
            ORDER BY bucket
            """)
    List<DashboardBucketCount> chartContests(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("dateFormat") String dateFormat);

    @Select("""
            SELECT DATE_FORMAT(created_at, #{dateFormat}) AS bucket, COUNT(*) AS count
            FROM solutions
            WHERE created_at >= #{start} AND created_at <= #{end}
            GROUP BY DATE_FORMAT(created_at, #{dateFormat})
            ORDER BY bucket
            """)
    List<DashboardBucketCount> chartSolutions(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("dateFormat") String dateFormat);

    @Select("""
            SELECT DATE_FORMAT(created_at, #{dateFormat}) AS bucket, COUNT(*) AS count
            FROM forum_posts
            WHERE created_at >= #{start} AND created_at <= #{end}
            GROUP BY DATE_FORMAT(created_at, #{dateFormat})
            ORDER BY bucket
            """)
    List<DashboardBucketCount> chartForumPosts(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("dateFormat") String dateFormat);
}
