package com.ulticode.auth.permission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.auth.permission.entity.RolePermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for {@link RolePermission}.
 */
@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {
}
