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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
  private final Clock clock;

    /**
     * Create a new refresh token for a user.
     *
     * @param userId   the user ID
     * @return the generated refresh token
     */
    public String createToken(String userId) {
        String tokenId = IdUtil.fastSimpleUUID();
        String token = jwtTokenProvider.generateRefreshToken(userId);
        String tokenHash = DigestUtil.sha256Hex(token);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(tokenId);
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(LocalDateTime.now(clock).plusNanos(
            jwtProperties.getRefreshTokenExpiration() * 1_000_000));
        refreshToken.setCreatedAt(LocalDateTime.now(clock));
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
     * @return the user ID and new refresh token
     * @throws BusinessException if the token is invalid or expired
     */
    @Transactional
    public RotationResult validateAndRotate(String token) {
        String userId = jwtTokenProvider.getUserIdFromRefreshToken(token);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }
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
        if (!userId.equals(storedToken.getUserId())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            log.warn("Expired refresh token for user: {}", storedToken.getUserId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token has expired");
        }

        int revoked = refreshTokenMapper.revokeIfActive(storedToken.getId());
        if (revoked != 1) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token has already been used");
        }

        log.info("Rotating refresh token for user: {}", storedToken.getUserId());
        return new RotationResult(storedToken.getUserId(), createToken(storedToken.getUserId()));
    }

    public void revokePresentedToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        refreshTokenMapper.update(null,
            new LambdaUpdateWrapper<RefreshToken>()
                .set(RefreshToken::getIsRevoked, true)
                .set(RefreshToken::getRotatedAt, LocalDateTime.now(clock))
                .eq(RefreshToken::getTokenHash, DigestUtil.sha256Hex(token))
                .eq(RefreshToken::getIsRevoked, false)
        );
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
                .set(RefreshToken::getRotatedAt, LocalDateTime.now(clock))
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
                .set(RefreshToken::getRotatedAt, LocalDateTime.now(clock))
                .eq(RefreshToken::getUserId, userId)
                .eq(RefreshToken::getIsRevoked, false)
        );
        log.info("Revoked {} refresh tokens for user: {}", count, userId);
    }

    public record RotationResult(String userId, String token) {}
}
