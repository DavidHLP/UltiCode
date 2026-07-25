package com.ulticode.modules.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.moderation.entity.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Mapper interface for Report entity.
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {

    /**
     * Find all reports for a specific entity.
     *
     * @param entityType the entity type
     * @param entityId   the entity ID
     * @return list of reports
     */
    @Select("SELECT * FROM reports WHERE entity_type = #{entityType} AND entity_id = #{entityId} ORDER BY created_at DESC")
    List<Report> findByEntity(@Param("entityType") String entityType, @Param("entityId") String entityId);

    /**
     * Find all reports in a queue item.
     *
     * @param queueId the queue item ID
     * @return list of reports
     */
    @Select("SELECT * FROM reports WHERE queue_id = #{queueId} ORDER BY created_at DESC")
    List<Report> findByQueueId(@Param("queueId") String queueId);

    /**
     * Find all reports by a specific reporter.
     *
     * @param reporterId the reporter ID
     * @return list of reports
     */
    @Select("SELECT * FROM reports WHERE reporter_id = #{reporterId} ORDER BY created_at DESC")
    List<Report> findByReporterId(@Param("reporterId") String reporterId);

    /**
     * Count reports for a specific entity.
     *
     * @param entityType the entity type
     * @param entityId   the entity ID
     * @return count of reports
     */
    @Select("SELECT COUNT(*) FROM reports WHERE entity_type = #{entityType} AND entity_id = #{entityId}")
    long countByEntity(@Param("entityType") String entityType, @Param("entityId") String entityId);

    /**
     * Check if a user has already reported an entity.
     *
     * @param reporterId the reporter ID
     * @param entityType the entity type
     * @param entityId   the entity ID
     * @return count of existing reports
     */
    @Select("SELECT COUNT(*) FROM reports WHERE reporter_id = #{reporterId} AND entity_type = #{entityType} AND entity_id = #{entityId}")
    long countByReporterAndEntity(@Param("reporterId") String reporterId, @Param("entityType") String entityType, @Param("entityId") String entityId);

    /**
     * Update status of all reports linked to a queue item.
     *
     * @param queueId the queue item ID
     * @param status  the new status
     * @return number of rows affected
     */
    @Update("UPDATE reports SET status = #{status}, updated_at = NOW() WHERE queue_id = #{queueId}")
    int updateStatusByQueueId(@Param("queueId") String queueId, @Param("status") String status);
}
