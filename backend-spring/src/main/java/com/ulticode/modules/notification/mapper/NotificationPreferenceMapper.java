package com.ulticode.modules.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.notification.entity.NotificationPreference;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/**
 * Mapper for NotificationPreference entity.
 */
@Mapper
public interface NotificationPreferenceMapper extends BaseMapper<NotificationPreference> {

    @Select("SELECT * FROM notification_preferences WHERE user_id = #{userId}")
    Optional<NotificationPreference> findByUserId(@Param("userId") String userId);
}
