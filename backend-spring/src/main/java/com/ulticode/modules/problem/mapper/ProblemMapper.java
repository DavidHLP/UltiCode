package com.ulticode.modules.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problem.entity.Problem;
import com.ulticode.modules.user.dto.DifficultyCountDTO;
import org.apache.ibatis.annotations.Arg;
import org.apache.ibatis.annotations.ConstructorArgs;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * MyBatis-Plus mapper for Problem entity.
 * Provides standard CRUD operations through BaseMapper.
 */
@Mapper
public interface ProblemMapper extends BaseMapper<Problem> {

    /**
     * Count published problems grouped by difficulty.
     *
     * @return list of DifficultyCountDTO containing [difficulty, count]
     */
    @ConstructorArgs({
            @Arg(column = "difficulty", javaType = String.class),
            @Arg(column = "count", javaType = Long.class)
    })
    @Select("SELECT difficulty, COUNT(*) as count FROM problems " +
            "WHERE is_deleted = false AND is_published = true " +
            "GROUP BY difficulty")
    List<DifficultyCountDTO> countByDifficulty();

    /**
     * DTO for batch-fetching problem tag relations.
     */
    record ProblemTagDTO(Long problemId, String tagName) {}

    /**
     * Batch-fetch all tag names for a list of problem IDs.
     * Eliminates N+1 by fetching all tag relations in a single IN query.
     *
     * @param problemIds list of problem IDs
     * @return list of ProblemTagDTO with problemId and tagName
     */
    @ConstructorArgs({
            @Arg(column = "problem_id", javaType = Long.class),
            @Arg(column = "tag_name", javaType = String.class)
    })
    @Select("<script>" +
            "SELECT ptr.problem_id, pt.label as tag_name " +
            "FROM problem_tag_relations ptr " +
            "LEFT JOIN problem_tags pt ON ptr.tag_id = pt.id " +
            "WHERE ptr.problem_id IN " +
            "<foreach collection='problemIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach>" +
            "</script>")
    List<ProblemTagDTO> selectTagsByProblemIds(@Param("problemIds") List<Long> problemIds);

    @Update("UPDATE problems SET is_flagged = #{isFlagged}, flagged_reason = #{reason}, flagged_at = CASE WHEN #{isFlagged} = true THEN NOW() ELSE NULL END WHERE id = #{id}")
    int updateFlagStatus(@Param("id") String id, @Param("isFlagged") boolean isFlagged, @Param("reason") String reason);
}
