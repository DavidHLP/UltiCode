package com.ulticode.modules.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Dashboard mapper for statistics queries.
 */
@Mapper
public interface DashboardMapper {

    // Problem statistics
    @Select("SELECT COUNT(*) FROM problems")
    Long countTotalProblems();

    @Select("SELECT COUNT(*) FROM problems WHERE is_published = 1")
    Long countPublishedProblems();

    @Select("SELECT difficulty, COUNT(*) as count FROM problems GROUP BY difficulty")
    List<Map<String, Object>> countProblemsByDifficultyRaw();

    @Select("SELECT status, COUNT(*) as count FROM problems GROUP BY status")
    List<Map<String, Object>> countProblemsByStatusRaw();

    // Contest statistics
    @Select("SELECT COUNT(*) FROM contests")
    Long countTotalContests();

    @Select("SELECT COUNT(*) FROM contests WHERE start_time > #{now}")
    Long countUpcomingContests(LocalDateTime now);

    @Select("SELECT COUNT(*) FROM contests WHERE start_time <= #{now} AND end_time > #{now}")
    Long countRunningContests(LocalDateTime now);

    @Select("SELECT COUNT(*) FROM contests WHERE end_time <= #{now}")
    Long countFinishedContests(LocalDateTime now);

    // Submission statistics
    @Select("SELECT COUNT(*) FROM submissions")
    Long countTotalSubmissions();

    @Select("SELECT COUNT(*) FROM submissions WHERE created_at >= #{since}")
    Long countSubmissionsSince(LocalDateTime since);

    @Select("SELECT COALESCE(SUM(CASE WHEN status = 'Accepted' THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) FROM submissions")
    Double calculateAcceptanceRate();

    // Solution statistics
    @Select("SELECT COUNT(*) FROM solutions")
    Long countTotalSolutions();

    @Select("SELECT COUNT(*) FROM solutions WHERE is_published = 1")
    Long countPublishedSolutions();

    @Select("SELECT COUNT(*) FROM solutions WHERE is_flagged = 1")
    Long countFlaggedSolutions();

    // Forum statistics
    @Select("SELECT COUNT(*) FROM forum_posts WHERE is_deleted = 0")
    Long countForumPosts();

    @Select("SELECT COUNT(*) FROM forum_comments WHERE is_deleted = 0")
    Long countForumComments();

    @Select("SELECT COUNT(*) FROM forum_communities")
    Long countForumCommunities();

    @Select("SELECT COUNT(*) FROM forum_posts WHERE is_deleted = 0 AND is_flagged = 1")
    Long countFlaggedPosts();

    @Select("SELECT COUNT(*) FROM forum_comments WHERE is_deleted = 0 AND is_flagged = 1")
    Long countFlaggedComments();

    @Select("""
            SELECT DATE_FORMAT(created_at, #{dateFormat}) as date, COUNT(*) as count
            FROM submissions
            WHERE created_at >= #{start} AND created_at <= #{end}
            GROUP BY DATE_FORMAT(created_at, #{dateFormat})
            ORDER BY date
            """)
    List<Map<String, Object>> getSubmissionsChartData(LocalDateTime start, LocalDateTime end, String dateFormat);

    @Select("""
            SELECT DATE_FORMAT(published_at, #{dateFormat}) as date, COUNT(*) as count
            FROM problems
            WHERE published_at >= #{start} AND published_at <= #{end}
            GROUP BY DATE_FORMAT(published_at, #{dateFormat})
            ORDER BY date
            """)
    List<Map<String, Object>> getProblemsChartData(LocalDateTime start, LocalDateTime end, String dateFormat);

    @Select("""
            SELECT DATE_FORMAT(created_at, #{dateFormat}) as date, COUNT(*) as count
            FROM contests
            WHERE created_at >= #{start} AND created_at <= #{end}
            GROUP BY DATE_FORMAT(created_at, #{dateFormat})
            ORDER BY date
            """)
    List<Map<String, Object>> getContestsChartData(LocalDateTime start, LocalDateTime end, String dateFormat);

    @Select("""
            SELECT DATE_FORMAT(created_at, #{dateFormat}) as date, COUNT(*) as count
            FROM solutions
            WHERE created_at >= #{start} AND created_at <= #{end}
            GROUP BY DATE_FORMAT(created_at, #{dateFormat})
            ORDER BY date
            """)
    List<Map<String, Object>> getSolutionsChartData(LocalDateTime start, LocalDateTime end, String dateFormat);

    @Select("""
            SELECT DATE_FORMAT(created_at, #{dateFormat}) as date, COUNT(*) as count
            FROM forum_posts
            WHERE is_deleted = 0 AND created_at >= #{start} AND created_at <= #{end}
            GROUP BY DATE_FORMAT(created_at, #{dateFormat})
            ORDER BY date
            """)
    List<Map<String, Object>> getForumPostsChartData(LocalDateTime start, LocalDateTime end, String dateFormat);
}
