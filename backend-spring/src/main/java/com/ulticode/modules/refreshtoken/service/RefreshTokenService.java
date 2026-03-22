package com.ulticode.modules.refreshtoken.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.refreshtoken.entity.RefreshToken;
import com.ulticode.modules.refreshtoken.mapper.RefreshTokenMapper;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Refresh Token service.
 * Manages refresh token creation, validation, and rotation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenMapper refreshTokenMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    /**
     * Create a new refresh token for a user.
     *
     * @param userId   the user ID
     * @param response the HTTP response (for setting cookies if needed)
     * @return the generated refresh token
     */
    public String createToken(String userId, HttpServletResponse response) {
        String tokenId = IdUtil.fastSimpleUUID();
        String token = jwtTokenProvider.generateRefreshToken(userId);
        String tokenHash = DigestUtil.sha256Hex(token);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(tokenId);
        refreshToken.setUserId(userId);
        refreshToken.setToken(token);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(7));
        refreshToken.setCreatedAt(LocalDateTime.now());
        refreshToken.setIsRevoked(false);

        refreshTokenMapper.insert(refreshToken);
        log.debug("Created refresh token for user: {}", userId);

        return token;
    }

    /**
     * Validate and rotate a refresh token.
     * If valid, revokes the old token and returns a new one.
     *
     * @param token    the refresh token to validate
     * @param response the HTTP response (for setting cookies if needed)
     * @return a new refresh token
     * @throws BusinessException if the token is invalid or expired
     */
    public String validateAndRotate(String token, HttpServletResponse response) {
        String tokenHash = DigestUtil.sha256Hex(token);

        RefreshToken storedToken = refreshTokenMapper.selectOne(
            new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, tokenHash)
                .eq(RefreshToken::getIsRevoked, false)
        );

        if (storedToken == null) {
            log.warn("Invalid refresh token attempt");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Expired refresh token for user: {}", storedToken.getUserId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token has expired");
        }

        // Revoke old token
        revokeToken(storedToken.getId());

        // Generate new token
        log.info("Rotating refresh token for user: {}", storedToken.getUserId());
        return createToken(storedToken.getUserId(), response);
    }

    /**
     * Revoke a single refresh token by ID.
     *
     * @param tokenId the token ID to revoke
     */
    public void revokeToken(String tokenId) {
        refreshTokenMapper.update(null,
            new LambdaUpdateWrapper<RefreshToken>()
                .set(RefreshToken::getIsRevoked, true)
                .set(RefreshToken::getRotatedAt, LocalDateTime.now())
                .eq(RefreshToken::getId, tokenId)
        );
    }

    /**
     * Revoke all refresh tokens for a user.
     * Used when user logs out from all devices or changes password.
     *
     * @param userId the user ID
     */
    public void revokeAllUserTokens(String userId) {
        int count = refreshTokenMapper.update(null,
            new LambdaUpdateWrapper<RefreshToken>()
                .set(RefreshToken::getIsRevoked, true)
                .set(RefreshToken::getRotatedAt, LocalDateTime.now())
                .eq(RefreshToken::getUserId, userId)
                .eq(RefreshToken::getIsRevoked, false)
        );
        log.info("Revoked {} refresh tokens for user: {}", count, userId);
    }
}
