package com.ulticode.modules.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Mapper for problem_tag_relations table.
 */
@Mapper
public interface ProblemTagRelationMapper extends BaseMapper<ProblemTagRelation> {

    /**
     * Find all tag IDs for a problem.
     */
    @Select("SELECT tag_id FROM problem_tag_relations WHERE problem_id = #{problemId}")
    List<String> findTagIdsByProblemId(@Param("problemId") Long problemId);

    /**
     * Find all problem IDs for a tag.
     */
    @Select("SELECT problem_id FROM problem_tag_relations WHERE tag_id = #{tagId}")
    List<Long> findProblemIdsByTagId(@Param("tagId") String tagId);

    /**
     * Find tag statistics for a user based on solved problems.
     * Returns tags with the count of problems solved by the user in each tag.
     *
     * @param userId the user ID
     * @return list of maps containing tagName, tagSlug, and count
     */
    @Select("SELECT pt.label AS tagName, pt.slug AS tagSlug, COUNT(DISTINCT ptr.problem_id) AS count " +
            "FROM problem_tag_relations ptr " +
            "JOIN problem_tags pt ON ptr.tag_id = pt.id " +
            "JOIN submissions s ON ptr.problem_id = s.problem_id " +
            "WHERE s.user_id = #{userId} AND s.status = 'Accepted' " +
            "GROUP BY pt.id, pt.label, pt.slug " +
            "ORDER BY count DESC")
    List<Map<String, Object>> findTagStatsByUserId(@Param("userId") String userId);
}
