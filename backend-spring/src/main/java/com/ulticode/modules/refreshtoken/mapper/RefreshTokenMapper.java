package com.ulticode.modules.refreshtoken.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.modules.refreshtoken.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis-Plus mapper for RefreshToken entity.
 * Provides standard CRUD operations through BaseMapper.
 */
@Mapper
public interface RefreshTokenMapper extends BaseMapper<RefreshToken> {

    @Update("""
        UPDATE refresh_tokens
        SET is_revoked = 1, rotated_at = CURRENT_TIMESTAMP(3)
        WHERE id = #{tokenId} AND is_revoked = 0
        """)
    int revokeIfActive(@Param("tokenId") String tokenId);
}
