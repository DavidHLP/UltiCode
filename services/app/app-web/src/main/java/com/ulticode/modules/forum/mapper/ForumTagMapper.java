package com.ulticode.modules.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.forum.entity.ForumTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus mapper for ForumTag entity.
 * Extends BaseMapper for basic CRUD operations and provides custom query methods.
 */
@Mapper
public interface ForumTagMapper extends BaseMapper<ForumTag> {
    /**
     * Load a tag while serializing mutations that address the same row.
     *
     * @param id tag ID
     * @return the locked tag, or {@code null} when it does not exist
     */
    @Select("SELECT * FROM forum_tags WHERE id = #{id} FOR UPDATE")
    ForumTag selectByIdForUpdate(@Param("id") String id);


    /**
     * Find tag by slug.
     *
     * @param slug the unique slug
     * @return the tag if found
     */
    @Select("SELECT * FROM forum_tags WHERE slug = #{slug} LIMIT 1")
    Optional<ForumTag> findBySlug(@Param("slug") String slug);

    /**
     * Find tag by name (case insensitive).
     *
     * @param name the tag name
     * @return the tag if found
     */
    @Select("SELECT * FROM forum_tags WHERE LOWER(name) = LOWER(#{name}) LIMIT 1")
    Optional<ForumTag> findByName(@Param("name") String name);

    /**
     * Find all tags ordered by usage count.
     *
     * @return list of tags ordered by popularity
     */
    @Select("SELECT * FROM forum_tags ORDER BY usage_count DESC")
    List<ForumTag> findAllOrderByUsage();

    /**
     * Find popular tags.
     *
     * @param limit maximum number of tags to return
     * @return list of popular tags
     */
    @Select("SELECT * FROM forum_tags ORDER BY usage_count DESC LIMIT #{limit}")
    List<ForumTag> findPopularTags(@Param("limit") int limit);

    /**
     * Search tags by name.
     *
     * @param keyword the search keyword
     * @param limit   maximum number of tags to return
     * @return list of matching tags
     */
    @Select("SELECT * FROM forum_tags WHERE name LIKE CONCAT('%', #{keyword}, '%') ORDER BY usage_count DESC LIMIT #{limit}")
    List<ForumTag> searchTags(@Param("keyword") String keyword, @Param("limit") int limit);

    /**
     * Find tags by color.
     *
     * @param color the color code
     * @return list of tags with the given color
     */
    @Select("SELECT * FROM forum_tags WHERE color = #{color} ORDER BY name ASC")
    List<ForumTag> findByColor(@Param("color") String color);

    /**
     * Count all tags.
     *
     * @return total count of tags
     */
    @Select("SELECT COUNT(*) FROM forum_tags")
    long countAll();

    /**
     * Increment usage count.
     *
     * @param tagId the tag ID
     * @return number of rows affected
     */
    @Update("UPDATE forum_tags SET usage_count = usage_count + 1 WHERE id = #{tagId}")
    int incrementUsageCount(@Param("tagId") String tagId);

    /**
     * Decrement usage count (minimum 0).
     *
     * @param tagId the tag ID
     * @return number of rows affected
     */
    @Update("UPDATE forum_tags SET usage_count = GREATEST(usage_count - 1, 0) WHERE id = #{tagId}")
    int decrementUsageCount(@Param("tagId") String tagId);

    /**
     * Check if slug exists.
     *
     * @param slug the slug to check
     * @return true if slug exists
     */
    @Select("SELECT COUNT(*) > 0 FROM forum_tags WHERE slug = #{slug}")
    boolean existsBySlug(@Param("slug") String slug);

    /**
     * Check if name exists (case insensitive).
     *
     * @param name the name to check
     * @return true if name exists
     */
    @Select("SELECT COUNT(*) > 0 FROM forum_tags WHERE LOWER(name) = LOWER(#{name})")
    boolean existsByName(@Param("name") String name);

    /**
     * Find tags by IDs.
     *
     * @param ids list of tag IDs
     * @return list of tags
     */
    @Select("<script>" +
            "SELECT * FROM forum_tags WHERE id IN " +
            "<foreach item='id' collection='ids' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            " ORDER BY usage_count DESC" +
            "</script>")
    List<ForumTag> findByIds(@Param("ids") List<String> ids);
}
