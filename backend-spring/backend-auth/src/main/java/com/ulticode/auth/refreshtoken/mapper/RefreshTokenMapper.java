package com.ulticode.auth.refreshtoken.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ulticode.auth.refreshtoken.entity.RefreshToken;
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

    /**
     * AUTH-COMP-005: revoke an entire token family (token-theft response).
     */
    @Update("""
        UPDATE refresh_tokens
        SET is_revoked = 1, rotated_at = CURRENT_TIMESTAMP(3)
        WHERE family_id = #{familyId} AND is_revoked = 0
        """)
    int revokeFamily(@Param("familyId") String familyId);

    /**
     * AUTH-COMP-005: set the forward/backward chain links between rotated siblings.
     */
    @Update("""
        UPDATE refresh_tokens
        SET replaced_by_token_id = #{newTokenId}
        WHERE id = #{oldTokenId}
        """)
    int setReplacedBy(@Param("oldTokenId") String oldTokenId, @Param("newTokenId") String newTokenId);
}
