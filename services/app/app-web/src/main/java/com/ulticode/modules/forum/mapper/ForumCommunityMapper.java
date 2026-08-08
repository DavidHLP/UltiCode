package com.ulticode.modules.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.forum.entity.ForumCommunity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * MyBatis-Plus mapper for ForumCommunity entity.
 * Extends BaseMapper for basic CRUD operations and provides custom query methods.
 */
@Mapper
public interface ForumCommunityMapper extends BaseMapper<ForumCommunity> {

    /**
     * Find community by slug.
     *
     * @param slug the unique slug
     * @return the community if found
     */
    @Select("SELECT * FROM forum_communities WHERE slug = #{slug} LIMIT 1")
    ForumCommunity findBySlug(@Param("slug") String slug);

    /**
     * Find all official communities.
     *
     * @return list of official communities
     */
    @Select("SELECT * FROM forum_communities WHERE is_official = 1 ORDER BY sort_order ASC")
    List<ForumCommunity> findOfficialCommunities();

    /**
     * Find all featured communities.
     *
     * @return list of featured communities
     */
    @Select("SELECT * FROM forum_communities WHERE is_featured = 1 ORDER BY sort_order ASC")
    List<ForumCommunity> findFeaturedCommunities();

    /**
     * Find public communities.
     *
     * @return list of public communities
     */
    @Select("SELECT * FROM forum_communities WHERE visibility = 'PUBLIC' ORDER BY members DESC")
    List<ForumCommunity> findPublicCommunities();

    /**
     * Find communities by visibility.
     *
     * @param visibility the visibility type (PUBLIC, PRIVATE)
     * @return list of communities
     */
    @Select("SELECT * FROM forum_communities WHERE visibility = #{visibility} ORDER BY members DESC")
    List<ForumCommunity> findByVisibility(@Param("visibility") String visibility);

    /**
     * Find top communities by member count.
     *
     * @param limit maximum number of communities to return
     * @return list of top communities
     */
    @Select("SELECT * FROM forum_communities ORDER BY members DESC LIMIT #{limit}")
    List<ForumCommunity> findTopCommunities(@Param("limit") int limit);

    /**
     * Search communities by name or description.
     *
     * @param keyword the search keyword
     * @param limit   maximum number of communities to return
     * @return list of matching communities
     */
    @Select("SELECT * FROM forum_communities WHERE (name LIKE CONCAT('%', #{keyword}, '%') OR description LIKE CONCAT('%', #{keyword}, '%')) ORDER BY members DESC LIMIT #{limit}")
    List<ForumCommunity> searchCommunities(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * Count all communities.
     *
     * @return total count of communities
     */
    @Select("SELECT COUNT(*) FROM forum_communities")
    long countAll();

    /**
     * Increment member count.
     *
     * @param communityId the community ID
     * @return number of rows affected
     */
    @Update("UPDATE forum_communities SET members = members + 1 WHERE id = #{communityId}")
    int incrementMembers(@Param("communityId") String communityId);

    /**
     * Decrement member count (minimum 0).
     *
     * @param communityId the community ID
     * @return number of rows affected
     */
    @Update("UPDATE forum_communities SET members = GREATEST(members - 1, 0) WHERE id = #{communityId}")
    int decrementMembers(@Param("communityId") String communityId);

    /**
     * Increment online count.
     *
     * @param communityId the community ID
     * @return number of rows affected
     */
    @Update("UPDATE forum_communities SET online = online + 1 WHERE id = #{communityId}")
    int incrementOnline(@Param("communityId") String communityId);

    /**
     * Decrement online count (minimum 0).
     *
     * @param communityId the community ID
     * @return number of rows affected
     */
    @Update("UPDATE forum_communities SET online = GREATEST(online - 1, 0) WHERE id = #{communityId}")
    int decrementOnline(@Param("communityId") String communityId);

    /**
     * Increment posts count.
     *
     * @param communityId the community ID
     * @return number of rows affected
     */
    @Update("UPDATE forum_communities SET posts_count = posts_count + 1, posts_today = posts_today + 1, posts_week = posts_week + 1 WHERE id = #{communityId}")
    int incrementPostsCount(@Param("communityId") String communityId);

    /**
     * Decrement posts count (minimum 0).
     *
     * @param communityId the community ID
     * @return number of rows affected
     */
    @Update("UPDATE forum_communities SET posts_count = GREATEST(posts_count - 1, 0) WHERE id = #{communityId}")
    int decrementPostsCount(@Param("communityId") String communityId);

    /**
     * Update featured status.
     *
     * @param communityId the community ID
     * @param isFeatured  the featured status
     * @return number of rows affected
     */
    @Update("UPDATE forum_communities SET is_featured = #{isFeatured} WHERE id = #{communityId}")
    int updateFeaturedStatus(@Param("communityId") String communityId, @Param("isFeatured") Boolean isFeatured);

    /**
     * Update official status.
     *
     * @param communityId the community ID
     * @param isOfficial  the official status
     * @return number of rows affected
     */
    @Update("UPDATE forum_communities SET is_official = #{isOfficial} WHERE id = #{communityId}")
    int updateOfficialStatus(@Param("communityId") String communityId, @Param("isOfficial") Boolean isOfficial);

    /**
     * Check if slug exists.
     *
     * @param slug the slug to check
     * @return true if slug exists
     */
    @Select("SELECT COUNT(*) > 0 FROM forum_communities WHERE slug = #{slug}")
    boolean existsBySlug(@Param("slug") String slug);
}
