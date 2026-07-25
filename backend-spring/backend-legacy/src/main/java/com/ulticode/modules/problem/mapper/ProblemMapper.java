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
    record ProblemTagDTO(Long problemId, String tagId, String tagName) {}

    /**
     * Batch-fetch all tag names for a list of problem IDs.
     * Eliminates N+1 by fetching all tag relations in a single IN query.
     *
     * @param problemIds list of problem IDs
     * @return list of ProblemTagDTO with problemId, tagId and tagName
     */
    @ConstructorArgs({
            @Arg(column = "problem_id", javaType = Long.class),
            @Arg(column = "tag_id", javaType = String.class),
            @Arg(column = "tag_name", javaType = String.class)
    })
    @Select("<script>" +
            "SELECT ptr.problem_id, pt.id as tag_id, pt.label as tag_name " +
            "FROM problem_tag_relations ptr " +
            "LEFT JOIN problem_tags pt ON ptr.tag_id = pt.id " +
            "WHERE ptr.problem_id IN " +
            "<foreach collection='problemIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach>" +
            "</script>")
    List<ProblemTagDTO> selectTagsByProblemIds(@Param("problemIds") List<Long> problemIds);

    @Update("UPDATE problems SET is_flagged = #{isFlagged}, flag_reason = #{reason}, flagged_at = CASE WHEN #{isFlagged} = true THEN NOW() ELSE NULL END WHERE id = #{id}")
    int updateFlagStatus(@Param("id") String id, @Param("isFlagged") boolean isFlagged, @Param("reason") String reason);

    @Update("<script>" +
            "UPDATE problems SET is_deleted = false, deleted_at = NULL, deleted_by = NULL " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach>" +
            "</script>")
    int restoreDeletedByIds(@Param("ids") List<Long> ids);

    record ProblemCountDTO(Long problemId, Long count) {}

    @ConstructorArgs({
            @Arg(column = "problem_id", javaType = Long.class),
            @Arg(column = "count", javaType = Long.class)
    })
    @Select("<script>" +
            "SELECT problem_id, COUNT(*) as count FROM submissions " +
            "WHERE problem_id IN " +
            "<foreach collection='problemIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach>" +
            " GROUP BY problem_id" +
            "</script>")
    List<ProblemCountDTO> countSubmissionsByProblemIds(@Param("problemIds") List<Long> problemIds);

    @ConstructorArgs({
            @Arg(column = "problem_id", javaType = Long.class),
            @Arg(column = "count", javaType = Long.class)
    })
    @Select("<script>" +
            "SELECT problem_id, COUNT(*) as count FROM solutions " +
            "WHERE problem_id IN " +
            "<foreach collection='problemIds' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach>" +
            " AND is_deleted = false" +
            " GROUP BY problem_id" +
            "</script>")
    List<ProblemCountDTO> countSolutionsByProblemIds(@Param("problemIds") List<Long> problemIds);

    @Select("<script>" +
            "SELECT * FROM problems WHERE is_deleted = true " +
            "<if test='search != null and search != \"\"'>" +
            " AND (id = #{search} OR title LIKE CONCAT('%',#{search},'%'))" +
            "</if>" +
            " ORDER BY created_at DESC" +
            " LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<Problem> selectDeletedProblems(@Param("search") String search, @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM problems WHERE is_deleted = true " +
            "<if test='search != null and search != \"\"'>" +
            " AND (id = #{search} OR title LIKE CONCAT('%',#{search},'%'))" +
            "</if>" +
            "</script>")
    long countDeletedProblems(@Param("search") String search);

    @Update("UPDATE problems SET is_flagged = true, flag_reason = #{reason}, " +
            "flag_reported_by = #{reportedBy}, flag_reported_at = NOW(), flag_status = 'PENDING' " +
            "WHERE id = #{id}")
    int flagProblem(@Param("id") Long id, @Param("reason") String reason, @Param("reportedBy") String reportedBy);

    @Update("<script>" +
            "UPDATE problems SET flag_status = #{status}, " +
            "flag_reviewed_by = #{reviewedBy}, flag_reviewed_at = NOW(), " +
            "flag_notes = #{notes}, " +
            "is_flagged = CASE WHEN #{status} = 'DISMISSED' THEN false ELSE is_flagged END " +
            "WHERE id = #{id}" +
            "</script>")
    int moderateProblem(@Param("id") Long id, @Param("status") String status,
                        @Param("notes") String notes, @Param("reviewedBy") String reviewedBy);

    @Select("<script>" +
            "SELECT * FROM problems WHERE is_flagged = true " +
            "<if test='status != null and status != \"\"'>" +
            " AND flag_status = #{status}" +
            "</if>" +
            " ORDER BY flag_reported_at DESC" +
            " LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<Problem> selectFlaggedProblems(@Param("status") String status,
                                        @Param("limit") int limit, @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM problems WHERE is_flagged = true " +
            "<if test='status != null and status != \"\"'>" +
            " AND flag_status = #{status}" +
            "</if>" +
            "</script>")
    long countFlaggedProblems(@Param("status") String status);

    @Update("<script>" +
            "UPDATE problems SET flag_status = #{status}, " +
            "flag_reviewed_by = #{reviewedBy}, flag_reviewed_at = NOW(), " +
            "flag_notes = #{notes}, " +
            "is_flagged = CASE WHEN #{status} = 'DISMISSED' THEN false ELSE is_flagged END " +
            "WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}</foreach>" +
            "</script>")
    int batchModerateProblems(@Param("ids") List<Long> ids, @Param("status") String status,
                              @Param("notes") String notes, @Param("reviewedBy") String reviewedBy);
}
