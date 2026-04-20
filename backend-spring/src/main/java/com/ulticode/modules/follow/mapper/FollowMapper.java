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

    @Select("SELECT * FROM user_follows WHERE following_id = #{followingId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<UserFollow> selectByFollowingIdPaged(@Param("followingId") String followingId, @Param("offset") long offset, @Param("limit") long limit);

    @Select("SELECT * FROM user_follows WHERE follower_id = #{followerId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<UserFollow> selectByFollowerIdPaged(@Param("followerId") String followerId, @Param("offset") long offset, @Param("limit") long limit);
}
