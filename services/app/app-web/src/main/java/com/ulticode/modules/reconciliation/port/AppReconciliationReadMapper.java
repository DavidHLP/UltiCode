package com.ulticode.modules.reconciliation.port;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** App-local reconciliation candidates; Auth parent existence is resolved via RPC. */
@Mapper
public interface AppReconciliationReadMapper {
    @Select("""
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = DATABASE()
              AND table_name IN (
                    'submissions', 'solutions', 'forum_posts', 'notifications',
                    'user_profiles', 'contest_participants', 'user_achievements',
                    'user_follows'
              )
            """)
    List<String> existingChildTables();

    @Select("""
            SELECT user_id AS account_id, COUNT(*) AS row_count
            FROM submissions
            WHERE user_id IS NOT NULL AND user_id > #{afterId}
            GROUP BY user_id ORDER BY user_id LIMIT #{limit}
            """)
    List<UserReferenceCount> submissionUserCounts(
            @Param("afterId") String afterId, @Param("limit") int limit);

    @Select("""
            SELECT user_id AS account_id, COUNT(*) AS row_count
            FROM solutions
            WHERE user_id IS NOT NULL AND user_id > #{afterId}
            GROUP BY user_id ORDER BY user_id LIMIT #{limit}
            """)
    List<UserReferenceCount> solutionUserCounts(
            @Param("afterId") String afterId, @Param("limit") int limit);

    @Select("""
            SELECT user_id AS account_id, COUNT(*) AS row_count
            FROM forum_posts
            WHERE user_id IS NOT NULL AND user_id > #{afterId}
            GROUP BY user_id ORDER BY user_id LIMIT #{limit}
            """)
    List<UserReferenceCount> forumPostUserCounts(
            @Param("afterId") String afterId, @Param("limit") int limit);

    @Select("""
            SELECT user_id AS account_id, COUNT(*) AS row_count
            FROM notifications
            WHERE user_id IS NOT NULL AND user_id > #{afterId}
            GROUP BY user_id ORDER BY user_id LIMIT #{limit}
            """)
    List<UserReferenceCount> notificationUserCounts(
            @Param("afterId") String afterId, @Param("limit") int limit);

    @Select("""
            SELECT account_id, COUNT(*) AS row_count
            FROM user_profiles
            WHERE account_id IS NOT NULL AND account_id > #{afterId}
            GROUP BY account_id ORDER BY account_id LIMIT #{limit}
            """)
    List<UserReferenceCount> userProfileAccountCounts(
            @Param("afterId") String afterId, @Param("limit") int limit);

    @Select("""
            SELECT user_id AS account_id, COUNT(*) AS row_count
            FROM contest_participants
            WHERE user_id IS NOT NULL AND user_id > #{afterId}
            GROUP BY user_id ORDER BY user_id LIMIT #{limit}
            """)
    List<UserReferenceCount> contestParticipantUserCounts(
            @Param("afterId") String afterId, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM user_profiles")
    long countUserProfiles();
}
