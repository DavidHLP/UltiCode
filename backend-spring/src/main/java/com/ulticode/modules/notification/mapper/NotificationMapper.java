package com.ulticode.modules.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
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

    @Select("SELECT COUNT(*) FROM notifications WHERE user_id = #{userId} AND is_read = 0")
    long countUnreadByUserId(@Param("userId") String userId);

    @Update("UPDATE notifications SET is_read = 1, read_at = NOW() WHERE user_id = #{userId} AND is_read = 0")
    int markAllAsRead(@Param("userId") String userId);

    @Update("UPDATE notifications SET is_read = 1, read_at = NOW() WHERE id = #{id}")
    int markAsRead(@Param("id") String id);

    @Insert("<script>INSERT INTO notifications " +
            "(id, user_id, type, category, title, body, link, metadata, announcement_id, is_read, read_at, created_at, updated_at) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.id}, #{item.userId}, #{item.type}, #{item.category}, #{item.title}, #{item.body}, #{item.link}, #{item.metadata}, #{item.announcementId}, #{item.isRead}, #{item.readAt}, #{item.createdAt}, #{item.updatedAt})" +
            "</foreach></script>")
    int batchInsert(@Param("list") List<Notification> list);

    /**
     * Paginated query for deduplicated system announcements by announcement_id.
     * Returns one representative notification per announcement group.
     */
    @Select("<script>" +
            "SELECT n.* FROM notifications n " +
            "INNER JOIN (" +
            "  SELECT announcement_id, MIN(id) AS representative_id " +
            "  FROM notifications " +
            "  WHERE category = #{category} " +
            "  <if test='keyword != null and keyword != \"\"'> AND title LIKE CONCAT('%', #{keyword}, '%') </if>" +
            "  <if test='type != null and type != \"\"'> AND type = #{type} </if>" +
            "  <if test='announcementId != null and announcementId != \"\"'> AND announcement_id = #{announcementId} </if>" +
            "  GROUP BY announcement_id" +
            ") dedup ON n.id = dedup.representative_id " +
            "ORDER BY " +
            "<choose>" +
            "  <when test='sortBy == \"title\"'>n.title</when>" +
            "  <when test='sortBy == \"type\"'>n.type</when>" +
            "  <when test='sortBy == \"category\"'>n.category</when>" +
            "  <when test='sortBy == \"announcementId\"'>n.announcement_id</when>" +
            "  <otherwise>n.created_at</otherwise>" +
            "</choose> " +
            "<choose>" +
            "  <when test='sortOrder == \"asc\"'>ASC</when>" +
            "  <otherwise>DESC</otherwise>" +
            "</choose>" +
            "</script>")
    IPage<Notification> selectDedupedAnnouncements(Page<Notification> page,
                                                    @Param("category") String category,
                                                    @Param("keyword") String keyword,
                                                    @Param("type") String type,
                                                    @Param("announcementId") String announcementId,
                                                    @Param("sortBy") String sortBy,
                                                    @Param("sortOrder") String sortOrder);
}
