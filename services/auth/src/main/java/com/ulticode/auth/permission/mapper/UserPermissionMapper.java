package com.ulticode.auth.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.auth.permission.entity.UserPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Mapper for {@link UserPermission}.
 */
@Mapper
public interface UserPermissionMapper extends BaseMapper<UserPermission> {

    @Select("<script>SELECT id, user_id AS userId, action, resource, granted_by AS grantedBy, "
            + "granted_at AS grantedAt, expires_at AS expiresAt FROM user_permissions "
            + "WHERE user_id IN <foreach collection='userIds' item='uid' open='(' separator=',' close=')'>#{uid}</foreach> "
            + "AND (expires_at IS NULL OR expires_at > #{now})</script>")
    List<UserPermission> selectActivePermissionsByUserIds(@Param("userIds") Set<String> userIds,
                                                           @Param("now") LocalDateTime now);
}
