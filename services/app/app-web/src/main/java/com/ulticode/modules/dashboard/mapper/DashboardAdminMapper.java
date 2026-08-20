package com.ulticode.modules.dashboard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * App-owner persistence seam for the Admin Dashboard aggregate provider.
 * Every table referenced here is App-owned.
 */
@Mapper
public interface DashboardAdminMapper {

    @Select("SELECT COUNT(*) FROM problems")
    Long countTotalProblems();

    @Select("SELECT COUNT(*) FROM problems WHERE is_published = 1")
    Long countPublishedProblems();

    @Select("SELECT difficulty AS bucket, COUNT(*) AS count FROM problems GROUP BY difficulty")
    List<Map<String, Object>> countProblemsByDifficulty();

    @Select("SELECT status AS bucket, COUNT(*) AS count FROM problems GROUP BY status")
    List<Map<String, Object>> countProblemsByStatus();

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

    @Select("SELECT COUNT(*) FROM solutions WHERE is_published = 1")
    Long countPublishedSolutions();

    @Select("SELECT COUNT(*) FROM solutions WHERE is_flagged = 1")
    Long countFlaggedSolutions();

    @Select("SELECT COUNT(*) FROM forum_posts WHERE is_deleted = 0")
    Long countForumPosts();

    @Select("SELECT COUNT(*) FROM forum_comments WHERE is_deleted = 0")
    Long countForumComments();

    @Select("SELECT COUNT(*) FROM forum_communities")
    Long countForumCommunities();

    @Select("SELECT COUNT(*) FROM forum_posts WHERE is_deleted = 0 AND is_flagged = 1")
    Long countFlaggedForumPosts();

    @Select("SELECT COUNT(*) FROM forum_comments WHERE is_deleted = 0 AND is_flagged = 1")
    Long countFlaggedForumComments();

    @Select("""
            SELECT DATE_FORMAT(published_at, #{dateFormat}) AS bucket, COUNT(*) AS count
            FROM problems
            WHERE published_at >= #{start} AND published_at <= #{end}
            GROUP BY DATE_FORMAT(published_at, #{dateFormat})
            ORDER BY bucket
            """)
    List<Map<String, Object>> chartProblems(
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
    List<Map<String, Object>> chartContests(
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
    List<Map<String, Object>> chartSolutions(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("dateFormat") String dateFormat);

    @Select("""
            SELECT DATE_FORMAT(created_at, #{dateFormat}) AS bucket, COUNT(*) AS count
            FROM forum_posts
            WHERE is_deleted = 0 AND created_at >= #{start} AND created_at <= #{end}
            GROUP BY DATE_FORMAT(created_at, #{dateFormat})
            ORDER BY bucket
            """)
    List<Map<String, Object>> chartForumPosts(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("dateFormat") String dateFormat);
}
