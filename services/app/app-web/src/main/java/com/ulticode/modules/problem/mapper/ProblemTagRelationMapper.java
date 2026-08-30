package com.ulticode.modules.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problem.entity.ProblemTagRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

}
