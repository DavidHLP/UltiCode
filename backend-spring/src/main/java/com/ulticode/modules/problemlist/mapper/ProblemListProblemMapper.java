package com.ulticode.modules.problemlist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.problemlist.entity.ProblemListProblemRelation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;

/**
 * MyBatis-Plus mapper for ProblemListProblemRelation entity.
 * Provides operations for managing problem-list relationships.
 */
@Mapper
public interface ProblemListProblemMapper extends BaseMapper<ProblemListProblemRelation> {

    /**
     * Find all problems in a list.
     *
     * @param listId the list ID
     * @return list of problem relations
     */
    @Select("SELECT * FROM problem_list_problem_relations WHERE list_id = #{listId} ORDER BY sort_order ASC, added_at ASC")
    List<ProblemListProblemRelation> findByListId(@Param("listId") String listId);

    /**
     * Find all lists containing a problem.
     *
     * @param problemId the problem ID
     * @return list of problem relations
     */
    @Select("SELECT * FROM problem_list_problem_relations WHERE problem_id = #{problemId}")
    List<ProblemListProblemRelation> findByProblemId(@Param("problemId") Long problemId);

    /**
     * Find a specific problem-list relation.
     *
     * @param listId    the list ID
     * @param problemId the problem ID
     * @return the relation if found
     */
    @Select("SELECT * FROM problem_list_problem_relations WHERE list_id = #{listId} AND problem_id = #{problemId}")
    Optional<ProblemListProblemRelation> findByListIdAndProblemId(
            @Param("listId") String listId,
            @Param("problemId") Long problemId);

    /**
     * Delete a problem from a list.
     *
     * @param listId    the list ID
     * @param problemId the problem ID
     * @return number of rows deleted
     */
    @Delete("DELETE FROM problem_list_problem_relations WHERE list_id = #{listId} AND problem_id = #{problemId}")
    int deleteByListIdAndProblemId(@Param("listId") String listId, @Param("problemId") Long problemId);

    /**
     * Delete all problems from a list.
     *
     * @param listId the list ID
     * @return number of rows deleted
     */
    @Delete("DELETE FROM problem_list_problem_relations WHERE list_id = #{listId}")
    int deleteByListId(@Param("listId") String listId);

    /**
     * Count problems in a list.
     *
     * @param listId the list ID
     * @return number of problems
     */
    @Select("SELECT COUNT(*) FROM problem_list_problem_relations WHERE list_id = #{listId}")
    long countByListId(@Param("listId") String listId);

    /**
     * Get the maximum sort order for a list.
     *
     * @param listId the list ID
     * @return the maximum sort order, or null if no problems
     */
    @Select("SELECT MAX(sort_order) FROM problem_list_problem_relations WHERE list_id = #{listId}")
    Integer getMaxSortOrder(@Param("listId") String listId);
}
