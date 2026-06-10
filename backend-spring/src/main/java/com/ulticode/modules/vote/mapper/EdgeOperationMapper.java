package com.ulticode.modules.vote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.vote.entity.EdgeOperation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * MyBatis-Plus mapper for EdgeOperation entity.
 * Provides standard CRUD operations through BaseMapper plus custom queries.
 */
@Mapper
public interface EdgeOperationMapper extends BaseMapper<EdgeOperation> {

    /**
     * Count operations by target and operation type
     *
     * @param targetId      the target ID
     * @param targetType    the target type
     * @param operationType the operation type
     * @return count of operations
     */
    @Select("SELECT COUNT(*) FROM edge_operations WHERE target_id = #{targetId} " +
            "AND target_type = #{targetType} AND operation_type = #{operationType}")
    int countByTargetAndOperation(@Param("targetId") String targetId,
                                   @Param("targetType") String targetType,
                                   @Param("operationType") String operationType);

    /**
     * Count operations by multiple targets and operation type.
     *
     * @param targetIds      the target IDs
     * @param targetType    the target type
     * @param operationType the operation type
     * @return list of maps with "target_id" and "cnt" keys
     */
    @Select("<script>SELECT target_id, COUNT(*) as cnt FROM edge_operations WHERE target_id IN " +
            "<foreach collection='targetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "AND target_type = #{targetType} AND operation_type = #{operationType} GROUP BY target_id</script>")
    List<Map<String, Object>> countByTargetsAndOperation(@Param("targetIds") List<String> targetIds,
                                                          @Param("targetType") String targetType,
                                                          @Param("operationType") String operationType);

    /**
     * Check if a specific operation exists for a user
     *
     * @param operatorId    the operator ID
     * @param targetId      the target ID
     * @param targetType    the target type
     * @param operationType the operation type
     * @return count (0 or 1)
     */
    @Select("SELECT COUNT(*) FROM edge_operations WHERE operator_id = #{operatorId} " +
            "AND target_id = #{targetId} AND target_type = #{targetType} " +
            "AND operation_type = #{operationType}")
    int existsByOperatorAndTarget(@Param("operatorId") String operatorId,
                                   @Param("targetId") String targetId,
                                   @Param("targetType") String targetType,
                                   @Param("operationType") String operationType);

    /**
     * Delete an operation by operator, target, and operation type
     *
     * @param operatorId    the operator ID
     * @param targetId      the target ID
     * @param targetType    the target type
     * @param operationType the operation type
     * @return number of rows deleted
     */
    @Delete("DELETE FROM edge_operations WHERE operator_id = #{operatorId} " +
            "AND target_id = #{targetId} AND target_type = #{targetType} " +
            "AND operation_type = #{operationType}")
    int deleteByOperatorAndTarget(@Param("operatorId") String operatorId,
                                   @Param("targetId") String targetId,
                                   @Param("targetType") String targetType,
                                   @Param("operationType") String operationType);

    /**
     * Find all vote operations by a specific operator for multiple targets.
     *
     * @param operatorId the operator ID
     * @param targetIds  the target IDs
     * @param targetType the target type
     * @return list of maps with "target_id" and "operation_type" keys
     */
    @Select("<script>SELECT target_id, operation_type FROM edge_operations " +
            "WHERE operator_id = #{operatorId} " +
            "AND target_id IN " +
            "<foreach collection='targetIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> " +
            "AND target_type = #{targetType}</script>")
    List<Map<String, Object>> findByOperatorAndTargets(
            @Param("operatorId") String operatorId,
            @Param("targetIds") List<String> targetIds,
            @Param("targetType") String targetType);

    /**
     * D-10: find this operator's LIKE/DISLIKE/FAVORITE on a single target problem.
     * Returns the most recent reaction (ORDER BY created_at DESC) when one user
     * has multiple rows for the same problem (the UNIQUE constraint is
     * (operator_id, operation_type, target_type, target_id) — does NOT span
     * operation_types, so one user can hold LIKE + DISLIKE rows simultaneously).
     * Returns null when the operator has no reaction.
     *
     * @param operatorId the operator ID
     * @param targetId   the target problem ID (as String to match edge_operations.target_id VARCHAR(40))
     * @param targetType the target type
     * @return reaction type ("LIKE" / "DISLIKE" / "FAVORITE") or null
     */
    @Select("SELECT operation_type FROM edge_operations " +
            "WHERE operator_id = #{operatorId} " +
            "AND target_id = #{targetId} " +
            "AND target_type = #{targetType} " +
            "AND operation_type IN ('LIKE','DISLIKE','FAVORITE') " +
            "ORDER BY created_at DESC " +
            "LIMIT 1")
    String findViewerReaction(
            @Param("operatorId") String operatorId,
            @Param("targetId") String targetId,
            @Param("targetType") String targetType);
}
