package com.ulticode.modules.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.moderation.entity.Appeal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Mapper interface for Appeal entity.
 */
@Mapper
public interface AppealMapper extends BaseMapper<Appeal> {

    /**
     * Find all appeals for a specific queue item.
     *
     * @param queueId the queue item ID
     * @return list of appeals
     */
    @Select("SELECT * FROM appeals WHERE queue_id = #{queueId} ORDER BY created_at DESC")
    List<Appeal> findByQueueId(@Param("queueId") String queueId);

    /**
     * Find all appeals by a specific appellant.
     *
     * @param appellantId the appellant ID
     * @return list of appeals
     */
    @Select("SELECT * FROM appeals WHERE appellant_id = #{appellantId} ORDER BY created_at DESC")
    List<Appeal> findByAppellantId(@Param("appellantId") String appellantId);

    /**
     * Find appeals by status.
     *
     * @param status the status to filter by
     * @return list of appeals
     */
    @Select("SELECT * FROM appeals WHERE status = #{status} ORDER BY created_at ASC")
    List<Appeal> findByStatus(@Param("status") String status);

    /**
     * Count pending appeals.
     *
     * @return count of pending appeals
     */
    @Select("SELECT COUNT(*) FROM appeals WHERE status = 'PENDING'")
    long countPending();

    @Select("SELECT COUNT(*) FROM appeals WHERE status = #{status}")
    long countByStatus(@Param("status") String status);
}
