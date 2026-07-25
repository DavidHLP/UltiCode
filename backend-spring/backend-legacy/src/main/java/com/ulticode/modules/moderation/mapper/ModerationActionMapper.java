package com.ulticode.modules.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.moderation.entity.ModerationAction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Mapper interface for ModerationAction entity.
 */
@Mapper
public interface ModerationActionMapper extends BaseMapper<ModerationAction> {

    /**
     * Find all actions for a specific queue item.
     *
     * @param queueId the queue item ID
     * @return list of actions
     */
    @Select("SELECT * FROM moderation_actions WHERE queue_id = #{queueId} ORDER BY created_at DESC")
    List<ModerationAction> findByQueueId(@Param("queueId") String queueId);

    /**
     * Find all actions performed by a specific moderator.
     *
     * @param performedById the moderator ID
     * @return list of actions
     */
    @Select("SELECT * FROM moderation_actions WHERE performed_by_id = #{performedById} ORDER BY created_at DESC")
    List<ModerationAction> findByPerformedBy(@Param("performedById") String performedById);
}
