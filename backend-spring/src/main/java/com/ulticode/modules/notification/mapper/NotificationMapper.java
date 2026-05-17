package com.ulticode.modules.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
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
            "(id, user_id, type, category, title, body, link, metadata, is_read, read_at, created_at, updated_at) VALUES " +
            "<foreach collection='list' item='item' separator=','>" +
            "(#{item.id}, #{item.userId}, #{item.type}, #{item.category}, #{item.title}, #{item.body}, #{item.link}, #{item.metadata}, #{item.isRead}, #{item.readAt}, #{item.createdAt}, #{item.updatedAt})" +
            "</foreach></script>")
    int batchInsert(@Param("list") List<Notification> list);
}
