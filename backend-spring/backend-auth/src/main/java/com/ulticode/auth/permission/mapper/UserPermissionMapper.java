package com.ulticode.auth.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.auth.permission.entity.UserPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for {@link UserPermission}.
 */
@Mapper
public interface UserPermissionMapper extends BaseMapper<UserPermission> {
}
