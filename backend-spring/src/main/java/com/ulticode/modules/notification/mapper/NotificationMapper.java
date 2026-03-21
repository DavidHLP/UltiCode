package com.ulticode.modules.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.notification.entity.Notification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
}
