package com.ulticode.modules.moderation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.moderation.entity.ModerationQueue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * Mapper interface for ModerationQueue entity.
 */
@Mapper
public interface ModerationQueueMapper extends BaseMapper<ModerationQueue> {

    /**
     * Find all queue items by status, ordered by priority and creation time.
     *
     * @param status the status to filter by
     * @return list of queue items
     */
    @Select("SELECT * FROM moderation_queue WHERE status = #{status} ORDER BY priority DESC, created_at ASC")
    List<ModerationQueue> findByStatus(@Param("status") String status);

    /**
     * Find queue item by entity type and entity ID.
     *
     * @param entityType the entity type
     * @param entityId   the entity ID
     * @return the queue item or null
     */
    @Select("SELECT * FROM moderation_queue WHERE entity_type = #{entityType} AND entity_id = #{entityId}")
    ModerationQueue findByEntity(@Param("entityType") String entityType, @Param("entityId") String entityId);

    /**
     * Assign a queue item to a moderator.
     *
     * @param id          the queue item ID
     * @param assignedTo  the moderator ID
     * @return number of rows affected
     */
    @Update("UPDATE moderation_queue SET assigned_to_id = #{assignedTo}, assigned_at = NOW(), status = 'UNDER_REVIEW', updated_at = NOW() WHERE id = #{id}")
    int assignToModerator(@Param("id") String id, @Param("assignedTo") String assignedTo);

    /**
     * Assign a queue item to a moderator only if currently unassigned.
     *
     * @param id          the queue item ID
     * @param moderatorId the moderator ID
     * @return number of rows affected (1 if assigned, 0 if already assigned)
     */
    @Update("UPDATE moderation_queue SET assigned_to_id = #{moderatorId}, assigned_at = NOW() WHERE id = #{id} AND assigned_to_id IS NULL")
    int assignToModeratorIfUnassigned(@Param("id") String id, @Param("moderatorId") String moderatorId);

    /**
     * Remove assignment from a queue item.
     *
     * @param id the queue item ID
     * @return number of rows affected
     */
    @Update("UPDATE moderation_queue SET assigned_to_id = NULL, assigned_at = NULL, status = 'PENDING', updated_at = NOW() WHERE id = #{id}")
    int unassign(@Param("id") String id);

    /**
     * Count pending queue items.
     *
     * @return count of pending items
     */
    @Select("SELECT COUNT(*) FROM moderation_queue WHERE status = 'PENDING'")
    long countPending();

    /**
     * Count items under review.
     *
     * @return count of items under review
     */
    @Select("SELECT COUNT(*) FROM moderation_queue WHERE status = 'UNDER_REVIEW'")
    long countUnderReview();

    /**
     * Count items resolved by a substantive action (delete / hide / warn / ban / restore /
     * appeal outcome). Excludes items dismissed as false reports so that resolvedCount and
     * dismissedCount partition all RESOLVED items without overlap.
     * IFNULL guards against hypothetically NULL resolution values on legacy rows.
     *
     * @return count of substantively resolved items
     */
    @Select("SELECT COUNT(*) FROM moderation_queue "
        + "WHERE status = 'RESOLVED' AND IFNULL(resolution, '') <> 'DISMISSED'")
    long countResolved();

    /**
     * Count items dismissed as false reports (status = RESOLVED, resolution = DISMISSED).
     *
     * @return count of dismissed items
     */
    @Select("SELECT COUNT(*) FROM moderation_queue "
        + "WHERE status = 'RESOLVED' AND resolution = 'DISMISSED'")
    long countDismissed();

    /**
     * Count items resolved today.
     *
     * @return count of items resolved today
     */
    @Select("SELECT COUNT(*) FROM moderation_queue WHERE status = 'RESOLVED' AND DATE(resolved_at) = CURDATE()")
    long countResolvedToday();

    /**
     * Find queue items assigned to a specific moderator.
     *
     * @param assignedToId the moderator ID
     * @return list of queue items
     */
    @Select("SELECT * FROM moderation_queue WHERE assigned_to_id = #{assignedToId} ORDER BY priority DESC, created_at ASC")
    List<ModerationQueue> findByAssignedTo(@Param("assignedToId") String assignedToId);

    /**
     * Calculate the average resolution time in hours for resolved moderation items.
     * Returns 0.0 when no resolved items exist (COALESCE handles NULL from empty AVG).
     *
     * @return average resolution time in hours
     */
    @Select("SELECT COALESCE(AVG(TIMESTAMPDIFF(HOUR, created_at, resolved_at)), 0) "
        + "FROM moderation_queue "
        + "WHERE status = 'RESOLVED' AND resolved_at IS NOT NULL")
    double avgResolutionTimeHours();

    /**
     * Count queue items grouped by primary category.
     * Returns rows of {key: category, value: count} for non-null categories.
     * Note: `key` is backtick-escaped because it is a MySQL reserved word.
     *
     * @return list of category-count maps, may be empty
     */
    @Select("SELECT primary_category AS `key`, COUNT(*) AS value FROM moderation_queue "
        + "WHERE primary_category IS NOT NULL GROUP BY primary_category "
        + "ORDER BY value DESC")
    List<Map<String, Object>> countByCategory();

    /**
     * Count queue items grouped by entity type.
     * Returns rows of {key: entityType, value: count} for non-null types.
     * Note: `key` is backtick-escaped because it is a MySQL reserved word.
     *
     * @return list of entity-type-count maps, may be empty
     */
    @Select("SELECT entity_type AS `key`, COUNT(*) AS value FROM moderation_queue "
        + "WHERE entity_type IS NOT NULL GROUP BY entity_type "
        + "ORDER BY value DESC")
    List<Map<String, Object>> countByEntityType();
}
