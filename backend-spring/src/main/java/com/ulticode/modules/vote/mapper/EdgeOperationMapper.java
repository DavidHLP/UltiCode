package com.ulticode.modules.vote.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.vote.entity.EdgeOperation;
import com.ulticode.modules.vote.entity.enums.EdgeOperationTargetType;
import com.ulticode.modules.vote.entity.enums.EdgeOperationType;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
