package com.ulticode.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for User entity.
 * Provides standard CRUD operations through BaseMapper.
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
