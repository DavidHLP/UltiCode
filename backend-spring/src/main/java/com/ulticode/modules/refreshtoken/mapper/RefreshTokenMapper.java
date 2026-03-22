package com.ulticode.modules.refreshtoken.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.refreshtoken.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus mapper for RefreshToken entity.
 * Provides standard CRUD operations through BaseMapper.
 */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {
}
