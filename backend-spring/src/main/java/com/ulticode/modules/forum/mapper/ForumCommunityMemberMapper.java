package com.ulticode.modules.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.forum.entity.ForumCommunityMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus mapper for ForumCommunityMember entity.
 * Extends BaseMapper for basic CRUD operations and provides custom query methods.
 */
@Mapper
public interface ForumCommunityMemberMapper extends BaseMapper<ForumCommunityMember> {

    /**
     * Find members by community ID.
     *
     * @param communityId the community ID
     * @return list of members ordered by join time
     */
    @Select("SELECT * FROM forum_community_members WHERE community_id = #{communityId} ORDER BY joined_at ASC")
    List<ForumCommunityMember> findByCommunityId(@Param("communityId") String communityId);

    /**
     * Find memberships by user ID.
     *
     * @param userId the user ID
     * @return list of memberships ordered by join time
     */
    @Select("SELECT * FROM forum_community_members WHERE user_id = #{userId} ORDER BY joined_at DESC")
    List<ForumCommunityMember> findByUserId(@Param("userId") String userId);

    /**
     * Find member by community ID and user ID.
     *
     * @param communityId the community ID
     * @param userId      the user ID
     * @return the member if found
     */
    @Select("SELECT * FROM forum_community_members WHERE community_id = #{communityId} AND user_id = #{userId} LIMIT 1")
    Optional<ForumCommunityMember> findByCommunityIdAndUserId(
            @Param("communityId") String communityId,
            @Param("userId") String userId
    );

    /**
     * Find members by community ID and role.
     *
     * @param communityId the community ID
     * @param role        the member role
     * @return list of members with the given role
     */
    @Select("SELECT * FROM forum_community_members WHERE community_id = #{communityId} AND role = #{role} ORDER BY joined_at ASC")
    List<ForumCommunityMember> findByCommunityIdAndRole(
            @Param("communityId") String communityId,
            @Param("role") String role
    );

    /**
     * Find moderators and admins for a community.
     *
     * @param communityId the community ID
     * @return list of moderators and admins
     */
    @Select("SELECT * FROM forum_community_members WHERE community_id = #{communityId} AND role IN ('MODERATOR', 'ADMIN', 'OWNER') ORDER BY role DESC, joined_at ASC")
    List<ForumCommunityMember> findModeratorsAndAdmins(@Param("communityId") String communityId);

    /**
     * Find community owner.
     *
     * @param communityId the community ID
     * @return the owner if found
     */
    @Select("SELECT * FROM forum_community_members WHERE community_id = #{communityId} AND role = 'OWNER' LIMIT 1")
    Optional<ForumCommunityMember> findOwner(@Param("communityId") String communityId);

    /**
     * Count members by community ID.
     *
     * @param communityId the community ID
     * @return count of members
     */
    @Select("SELECT COUNT(*) FROM forum_community_members WHERE community_id = #{communityId}")
    long countByCommunityId(@Param("communityId") String communityId);

    /**
     * Count memberships by user ID.
     *
     * @param userId the user ID
     * @return count of memberships
     */
    @Select("SELECT COUNT(*) FROM forum_community_members WHERE user_id = #{userId}")
    long countByUserId(@Param("userId") String userId);

    /**
     * Count members by community ID and role.
     *
     * @param communityId the community ID
     * @param role        the member role
     * @return count of members with the given role
     */
    @Select("SELECT COUNT(*) FROM forum_community_members WHERE community_id = #{communityId} AND role = #{role}")
    long countByCommunityIdAndRole(
            @Param("communityId") String communityId,
            @Param("role") String role
    );

    /**
     * Check if user is a member of a community.
     *
     * @param communityId the community ID
     * @param userId      the user ID
     * @return true if user is a member
     */
    @Select("SELECT COUNT(*) > 0 FROM forum_community_members WHERE community_id = #{communityId} AND user_id = #{userId}")
    boolean isMember(@Param("communityId") String communityId, @Param("userId") String userId);

    /**
     * Check if user has moderator or higher role.
     *
     * @param communityId the community ID
     * @param userId      the user ID
     * @return true if user is a moderator or higher
     */
    @Select("SELECT COUNT(*) > 0 FROM forum_community_members WHERE community_id = #{communityId} AND user_id = #{userId} AND role IN ('MODERATOR', 'ADMIN', 'OWNER')")
    boolean isModeratorOrHigher(@Param("communityId") String communityId, @Param("userId") String userId);

    /**
     * Check if user has admin or higher role.
     *
     * @param communityId the community ID
     * @param userId      the user ID
     * @return true if user is an admin or higher
     */
    @Select("SELECT COUNT(*) > 0 FROM forum_community_members WHERE community_id = #{communityId} AND user_id = #{userId} AND role IN ('ADMIN', 'OWNER')")
    boolean isAdminOrHigher(@Param("communityId") String communityId, @Param("userId") String userId);

    /**
     * Check if user is the owner.
     *
     * @param communityId the community ID
     * @param userId      the user ID
     * @return true if user is the owner
     */
    @Select("SELECT COUNT(*) > 0 FROM forum_community_members WHERE community_id = #{communityId} AND user_id = #{userId} AND role = 'OWNER'")
    boolean isOwner(@Param("communityId") String communityId, @Param("userId") String userId);

    /**
     * Update member role.
     *
     * @param id   the member record ID
     * @param role the new role
     * @return number of rows affected
     */
    @Update("UPDATE forum_community_members SET role = #{role} WHERE id = #{id}")
    int updateRole(@Param("id") String id, @Param("role") String role);

    /**
     * Delete membership by community ID and user ID.
     *
     * @param communityId the community ID
     * @param userId      the user ID
     * @return number of rows affected
     */
    @Update("DELETE FROM forum_community_members WHERE community_id = #{communityId} AND user_id = #{userId}")
    int deleteByCommunityIdAndUserId(
            @Param("communityId") String communityId,
            @Param("userId") String userId
    );
}
