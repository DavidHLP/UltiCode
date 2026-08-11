package com.ulticode.modules.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.modules.notification.entity.Notification;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Mapper for Notification entity.
 */
@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

 // Raw @Select/@Update bypass MyBatis-Plus @TableLogic, so we must add the
 // is_deleted=0 predicate explicitly to keep soft-deleted rows invisible
 // (Q12 follow-on). BaseMapper methods (selectById, deleteById, etc.)
 // auto-apply the filter via @TableLogic.

 @Select("SELECT COUNT(*) FROM notifications WHERE user_id = #{userId} AND is_read = 0 AND is_deleted = 0")
 long countUnreadByUserId(@Param("userId") String userId);

 @Update("UPDATE notifications SET is_read = 1, read_at = NOW(3) WHERE user_id = #{userId} AND is_read = 0 AND is_deleted = 0")
 int markAllAsRead(@Param("userId") String userId);

 @Update("UPDATE notifications SET is_read = 1, read_at = NOW(3) WHERE id = #{id} AND is_deleted = 0")
 int markAsRead(@Param("id") String id);

 /**
 * Batch insert notifications.
 *
 * <p>The {@code #{item.metadata}} parameter explicitly declares
 * {@link JacksonTypeHandler} because MyBatis does not inherit the
 * {@code @TableField(typeHandler=…)} metadata inside custom {@code @Insert}
 * SQL fragments. Without this, a {@code Map<String,Object>} field has no
 * JDBC type handler and MyBatis throws {@code IllegalStateException: Type
 * handler was null on parameter mapping for property '__frch_item_0.metadata'}.
 * Regression covered by {@code NotificationMapperBatchInsertTest}.
 */
 @Insert("<script>INSERT INTO notifications "
 + "(id, user_id, type, category, title, body, link, metadata, announcement_id, is_read, read_at, created_at, updated_at, is_deleted) VALUES "
 + "<foreach collection='list' item='item' separator=','>"
 + "(#{item.id}, #{item.userId}, #{item.type}, #{item.category}, #{item.title}, #{item.body}, #{item.link}, "
 + "#{item.metadata, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler}, "
 + "#{item.announcementId}, #{item.isRead}, #{item.readAt}, #{item.createdAt}, #{item.updatedAt}, #{item.deleted})"
 + "</foreach></script>")
 int batchInsert(@Param("list") List<Notification> list);

 /**
 * Paginated query for deduplicated system announcements by announcement_id.
 * Only broadcast copies with a non-null announcement_id are eligible; a
 * personal notification may share the category but is not an announcement.
 * Returns one representative notification per announcement group.
 */
 @Select("<script>"
 + "SELECT n.* FROM notifications n "
 + "INNER JOIN ("
 + " SELECT announcement_id, MIN(id) AS representative_id "
 + " FROM notifications "
+ " WHERE category = #{category} AND announcement_id IS NOT NULL AND is_deleted = 0 "
 + " <if test='keyword != null and keyword != \"\"'> AND title LIKE CONCAT('%', #{keyword}, '%') </if>"
 + " <if test='type != null and type != \"\"'> AND type = #{type} </if>"
 + " <if test='announcementId != null and announcementId != \"\"'> AND announcement_id = #{announcementId} </if>"
 + " GROUP BY announcement_id"
 + ") dedup ON n.id = dedup.representative_id "
 + "WHERE n.is_deleted = 0 "
 + "ORDER BY "
 + "<choose>"
 + " <when test='sortBy == \"title\"'>n.title</when>"
 + " <when test='sortBy == \"type\"'>n.type</when>"
 + " <when test='sortBy == \"category\"'>n.category</when>"
 + " <when test='sortBy == \"announcementId\"'>n.announcement_id</when>"
 + " <otherwise>n.created_at</otherwise>"
 + "</choose> "
 + "<choose>"
 + " <when test='sortOrder == \"asc\"'>ASC</when>"
 + " <otherwise>DESC</otherwise>"
 + "</choose>"
 + "</script>")
 IPage<Notification> selectDedupedAnnouncements(Page<Notification> page,
 @Param("category") String category,
 @Param("keyword") String keyword,
 @Param("type") String type,
 @Param("announcementId") String announcementId,
 @Param("sortBy") String sortBy,
 @Param("sortOrder") String sortOrder);

 /** Soft-delete one notification or every user copy in its announcement group. */
 @Update("<script>UPDATE notifications SET is_deleted = 1, updated_at = NOW(3) "
 + "WHERE is_deleted = 0 AND "
 + "<choose>"
+ " <when test='announcementId != null and announcementId != \"\"'>announcement_id = #{announcementId}</when>"
+ " <otherwise>id = #{notificationId} AND announcement_id IS NOT NULL</otherwise>"
 + "</choose></script>")
 int softDeleteAnnouncement(@Param("notificationId") String notificationId,
 @Param("announcementId") String announcementId);

 /** Update the shared announcement fields on every user copy in the group. */
 @Update("<script>UPDATE notifications SET "
 + "<if test='title != null'>title = #{title}, </if>"
 + "<if test='body != null'>body = #{body}, </if>"
 + "<if test='type != null and type.trim() != \"\"'>type = #{type}, </if>"
 + "<if test='newCategory != null and newCategory != \"\"'>category = #{newCategory}, </if>"
 + "updated_at = NOW(3) WHERE is_deleted = 0 AND "
 + "<choose>"
+ " <when test='announcementId != null and announcementId != \"\"'>announcement_id = #{announcementId}"
+ " <if test='existingCategory != null and existingCategory != \"\"'> AND category = #{existingCategory}</if>"
+ "</when>"
+ " <otherwise>id = #{notificationId} AND announcement_id IS NOT NULL</otherwise>"
 + "</choose></script>")
 int updateAnnouncement(@Param("notificationId") String notificationId,
 @Param("announcementId") String announcementId,
 @Param("existingCategory") String existingCategory,
 @Param("title") String title,
 @Param("body") String body,
 @Param("type") String type,
 @Param("newCategory") String newCategory);
}
