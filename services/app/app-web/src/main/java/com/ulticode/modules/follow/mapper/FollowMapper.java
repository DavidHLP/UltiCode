package com.ulticode.modules.follow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.follow.entity.UserFollow;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * MyBatis-Plus mapper for UserFollow entity.
 */
@Mapper
public interface FollowMapper extends BaseMapper<UserFollow> {

    @Select("SELECT * FROM user_follows WHERE follower_id = #{followerId} ORDER BY created_at DESC")
    List<UserFollow> selectByFollowerId(String followerId);

    @Select("SELECT * FROM user_follows WHERE following_id = #{followingId} ORDER BY created_at DESC")
    List<UserFollow> selectByFollowingId(String followingId);

    @Select("SELECT COUNT(*) FROM user_follows WHERE follower_id = #{followerId}")
    int countByFollowerId(String followerId);

    @Select("SELECT COUNT(*) FROM user_follows WHERE following_id = #{followingId}")
    int countByFollowingId(String followingId);

    @Select("SELECT COUNT(*) > 0 FROM user_follows WHERE follower_id = #{followerId} AND following_id = #{followingId}")
    boolean exists(String followerId, String followingId);

    @Insert("INSERT INTO user_follows (follower_id, following_id, created_at) VALUES (#{followerId}, #{followingId}, NOW()) ON DUPLICATE KEY UPDATE created_at=created_at")
    void insertIdempotent(@Param("followerId") String followerId, @Param("followingId") String followingId);

    @Delete("DELETE FROM user_follows WHERE follower_id = #{followerId} AND following_id = #{followingId}")
    void deleteRelation(@Param("followerId") String followerId, @Param("followingId") String followingId);

    /**
     * Idempotent delete that returns affected row count, eliminating the need
     * for an explicit exists() check before delete. Use this in service code
     * to keep the unfollow path to a single round-trip.
     *
     * @return 1 if the relation was deleted, 0 if it did not exist
     */
    @Delete("""
        DELETE FROM user_follows
        WHERE follower_id = #{followerId} AND following_id = #{followingId}
        """)
    int deleteIfExists(@Param("followerId") String followerId, @Param("followingId") String followingId);

    @Select("SELECT * FROM user_follows WHERE following_id = #{followingId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<UserFollow> selectByFollowingIdPaged(@Param("followingId") String followingId, @Param("offset") long offset, @Param("limit") long limit);

    @Select("SELECT * FROM user_follows WHERE follower_id = #{followerId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<UserFollow> selectByFollowerIdPaged(@Param("followerId") String followerId, @Param("offset") long offset, @Param("limit") long limit);

    @Select("""
        SELECT uf.following_id AS userId,
               COUNT(DISTINCT uf.follower_id) AS followerCount
        FROM user_follows uf
        WHERE uf.following_id IN <foreach collection="userIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        GROUP BY uf.following_id
        """)
    @Results({
        @Result(property = "userId", column = "userId"),
        @Result(property = "followerCount", column = "followerCount"),
        @Result(property = "followingCount", column = "followingCount")
    })
    List<FollowCountDTO> batchFollowCounts(@Param("userIds") List<String> userIds);

    @Select("""
        SELECT uf.follower_id AS userId,
               COUNT(DISTINCT uf.following_id) AS followingCount
        FROM user_follows uf
        WHERE uf.follower_id IN <foreach collection="userIds" item="id" open="(" separator="," close=")">#{id}</foreach>
        GROUP BY uf.follower_id
        """)
    @Results({
        @Result(property = "userId", column = "userId"),
        @Result(property = "followerCount", column = "followerCount"),
        @Result(property = "followingCount", column = "followingCount")
    })
    List<FollowCountDTO> batchFollowingCounts(@Param("userIds") List<String> userIds);

    record FollowCountDTO(String userId, int followerCount, int followingCount) {}
}
