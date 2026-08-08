package com.ulticode.auth.refreshtoken.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.refreshtoken.entity.RefreshToken;
import com.ulticode.auth.refreshtoken.mapper.RefreshTokenMapper;
import com.ulticode.auth.security.jwt.JwtProperties;
import com.ulticode.auth.security.jwt.JwtTokenProvider;
import com.ulticode.common.error.BaseErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Refresh Token service.
 * Manages refresh token creation, validation, and rotation.
 *
 * <p>Canonical copy owned by backend-auth. The legacy twin at
 * {@code com.ulticode.modules.refreshtoken.service.RefreshTokenService}
 * was retired in P7-RETIRE-REFRESHTOKEN-001. The only intentional differences are:
 *
 * <ul>
 *   <li>Throws {@link AuthBusinessException} (which carries a
 *       {@link BaseErrorCode} for the four failure modes) instead of
 *       the legacy {@code BusinessException}. The numeric code is
 *       byte-identical ({@code UNAUTHORIZED} = 40100) so the wire
 *       envelope remains unchanged.
 *   <li>References the backend-auth JWT plumbing under
 *       {@code com.ulticode.auth.security.jwt.*}.
 * </ul>
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
        return createToken(userId, null, null);
    }

    /**
     * Create a refresh token, optionally inheriting a family and previous token.
     *
     * @param userId      the user ID
     * @param familyId    family id; null creates a new family (login), non-null reuses it (rotation)
     * @param previousId  id of the token being rotated out; null on initial login
     * @return the generated refresh token
     */
    public String createToken(String userId, String familyId, String previousId) {
        String tokenId = IdUtil.fastSimpleUUID();
        String token = jwtTokenProvider.generateRefreshToken(userId);
        String tokenHash = DigestUtil.sha256Hex(token);
        String effectiveFamilyId = (familyId != null) ? familyId : tokenId;

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(tokenId);
        refreshToken.setUserId(userId);
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setExpiresAt(LocalDateTime.now(clock).plusNanos(
            jwtProperties.getRefreshTokenExpiration() * 1_000_000));
        refreshToken.setCreatedAt(LocalDateTime.now(clock));
        refreshToken.setIsRevoked(false);
        refreshToken.setFamilyId(effectiveFamilyId);
        refreshToken.setPreviousTokenId(previousId);

        refreshTokenMapper.insert(refreshToken);
        log.debug("Created refresh token for user: {} (family: {})", userId, effectiveFamilyId);

        return token;
    }

    /**
     * Validate and rotate a refresh token.
     * If valid, revokes the old token and returns a new one.
     *
     * @param token    the refresh token to validate
     * @return the user ID and new refresh token
     * @throws AuthBusinessException if the token is invalid, expired,
     *         or has already been used.
     */
    @Transactional
    public RotationResult validateAndRotate(String token) {
        String userId = jwtTokenProvider.getUserIdFromRefreshToken(token);
        if (userId == null) {
            throw new AuthBusinessException(BaseErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }
        String tokenHash = DigestUtil.sha256Hex(token);

        RefreshToken storedToken = refreshTokenMapper.selectOne(
            new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, tokenHash)
        );

        if (storedToken == null) {
            log.warn("Invalid refresh token attempt");
            throw new AuthBusinessException(BaseErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }
        if (!userId.equals(storedToken.getUserId())) {
            throw new AuthBusinessException(BaseErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }

        // AUTH-COMP-005: reuse detection — a revoked token being presented again
        // indicates theft. Revoke the entire family and reject.
        if (Boolean.TRUE.equals(storedToken.getIsRevoked())) {
            log.warn("Refresh token reuse detected for user: {}, family: {}",
                    storedToken.getUserId(), storedToken.getFamilyId());
            if (storedToken.getFamilyId() != null) {
                int familyRevoked = refreshTokenMapper.revokeFamily(storedToken.getFamilyId());
                log.warn("Revoked {} tokens in family {} due to reuse detection",
                        familyRevoked, storedToken.getFamilyId());
            }
            throw new AuthBusinessException(BaseErrorCode.UNAUTHORIZED,
                    "Refresh token has been revoked — possible token theft");
        }

        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            log.warn("Expired refresh token for user: {}", storedToken.getUserId());
            throw new AuthBusinessException(BaseErrorCode.UNAUTHORIZED, "Refresh token has expired");
        }

        int revoked = refreshTokenMapper.revokeIfActive(storedToken.getId());
        if (revoked != 1) {
            throw new AuthBusinessException(BaseErrorCode.UNAUTHORIZED, "Refresh token has already been used");
        }

        // AUTH-COMP-005: create the rotation sibling in the same family and
        // write the forward/backward chain links.
        String familyId = storedToken.getFamilyId() != null
                ? storedToken.getFamilyId() : storedToken.getId();
        String newToken = createToken(storedToken.getUserId(), familyId, storedToken.getId());

        // Write forward link: old token -> new token
        String newTokenHash = DigestUtil.sha256Hex(newToken);
        RefreshToken newRecord = refreshTokenMapper.selectOne(
            new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getTokenHash, newTokenHash)
        );
        if (newRecord != null) {
            refreshTokenMapper.setReplacedBy(storedToken.getId(), newRecord.getId());
        }

        log.info("Rotating refresh token for user: {} (family: {})", storedToken.getUserId(), familyId);
        return new RotationResult(storedToken.getUserId(), newToken);
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
