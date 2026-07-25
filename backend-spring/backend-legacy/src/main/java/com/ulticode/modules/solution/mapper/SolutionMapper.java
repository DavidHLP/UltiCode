package com.ulticode.modules.solution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.solution.entity.Solution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * MyBatis-Plus mapper for Solution entity.
 */
@Mapper
public interface SolutionMapper extends BaseMapper<Solution> {

    @Update("UPDATE solutions SET is_flagged = #{isFlagged}, flagged_reason = #{reason}, flagged_at = CASE WHEN #{isFlagged} = true THEN NOW() ELSE NULL END WHERE id = #{id}")
    int updateFlagStatus(@Param("id") String id, @Param("isFlagged") boolean isFlagged, @Param("reason") String reason);

    /**
     * Count total solutions by user.
     *
     * @param userId user ID
     * @return count of total solutions
     */
    @Select("SELECT COUNT(*) FROM solutions WHERE user_id = #{userId} AND is_deleted = false")
    Long countByUserId(@Param("userId") String userId);

    @Select("<script>" +
            "SELECT * FROM solutions WHERE is_deleted = true " +
            "<if test='search != null and search != \"\"'>" +
            " AND (title LIKE CONCAT('%',#{search},'%') OR content LIKE CONCAT('%',#{search},'%'))" +
            "</if>" +
            "<if test='problemId != null'>" +
            " AND problem_id = #{problemId}" +
            "</if>" +
            "<if test='userId != null and userId != \"\"'>" +
            " AND user_id = #{userId}" +
            "</if>" +
            "<if test='isFlagged != null'>" +
            " AND is_flagged = #{isFlagged}" +
            "</if>" +
            "<if test='isPublished != null'>" +
            " AND is_published = #{isPublished}" +
            "</if>" +
            " <choose>" +
            " <when test='sortBy == \"title\"'>ORDER BY title</when>" +
            " <when test='sortBy == \"views\"'>ORDER BY views</when>" +
            " <when test='sortBy == \"updated_at\"'>ORDER BY updated_at</when>" +
            " <otherwise>ORDER BY created_at</otherwise>" +
            " </choose>" +
            " <choose>" +
            " <when test='sortOrder == \"ASC\"'>ASC</when>" +
            " <otherwise>DESC</otherwise>" +
            " </choose>" +
            " LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<Solution> selectDeletedSolutions(
            @Param("search") String search,
            @Param("problemId") Long problemId,
            @Param("userId") String userId,
            @Param("isFlagged") Boolean isFlagged,
            @Param("isPublished") Boolean isPublished,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM solutions WHERE is_deleted = true " +
            "<if test='search != null and search != \"\"'>" +
            " AND (title LIKE CONCAT('%',#{search},'%') OR content LIKE CONCAT('%',#{search},'%'))" +
            "</if>" +
            "<if test='problemId != null'>" +
            " AND problem_id = #{problemId}" +
            "</if>" +
            "<if test='userId != null and userId != \"\"'>" +
            " AND user_id = #{userId}" +
            "</if>" +
            "<if test='isFlagged != null'>" +
            " AND is_flagged = #{isFlagged}" +
            "</if>" +
            "<if test='isPublished != null'>" +
            " AND is_published = #{isPublished}" +
            "</if>" +
            "</script>")
    long countDeletedSolutions(
            @Param("search") String search,
            @Param("problemId") Long problemId,
            @Param("userId") String userId,
            @Param("isFlagged") Boolean isFlagged,
            @Param("isPublished") Boolean isPublished);
}
