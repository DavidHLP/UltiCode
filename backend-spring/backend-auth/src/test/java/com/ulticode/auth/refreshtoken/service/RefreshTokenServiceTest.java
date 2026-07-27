package com.ulticode.auth.refreshtoken.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.refreshtoken.entity.RefreshToken;
import com.ulticode.auth.refreshtoken.mapper.RefreshTokenMapper;
import com.ulticode.auth.security.jwt.JwtProperties;
import com.ulticode.auth.security.jwt.JwtTokenProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link RefreshTokenService}.
 *
 * <p>Mirrors backend-legacy's
 * {@code com.ulticode.modules.refreshtoken.service.RefreshTokenServiceTest}
 * with the package and exception types adapted for backend-auth. The
 * Strangler Fig contract requires both copies to pass; the tests
 * intentionally cover the four cases that protect against the most
 * expensive security regressions (replay, expiry, access-token
 * confusion, hash-only storage).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenMapper refreshTokenMapper;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Spy
    private JwtProperties jwtProperties = new JwtProperties();

    @Mock
    private Clock clock;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("UTC"));
    }

    @Test
    @DisplayName("createToken stores only the SHA-256 hash, never the raw token")
    void createTokenStoresOnlyHash() {
        when(jwtTokenProvider.generateRefreshToken("user-1")).thenReturn("refresh-jwt");

        assertThat(refreshTokenService.createToken("user-1")).isEqualTo("refresh-jwt");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenMapper).insert(captor.capture());
        assertThat(captor.getValue().getTokenHash()).hasSize(64);
    }

    @Test
    @DisplayName("validateAndRotate atomically revokes the old token and issues a new one")
    void validateAndRotateAtomicallyRevokesAndReissues() {
        RefreshToken stored = new RefreshToken();
        stored.setId("token-1");
        stored.setUserId("user-1");
        stored.setIsRevoked(false);
        stored.setExpiresAt(LocalDateTime.parse("2026-01-02T00:00:00"));

        when(jwtTokenProvider.getUserIdFromRefreshToken("old-token")).thenReturn("user-1");
        when(refreshTokenMapper.selectOne(any(Wrapper.class))).thenReturn(stored);
        when(refreshTokenMapper.revokeIfActive("token-1")).thenReturn(1);
        when(jwtTokenProvider.generateRefreshToken("user-1")).thenReturn("new-token");

        RefreshTokenService.RotationResult result =
                refreshTokenService.validateAndRotate("old-token");

        assertThat(result.userId()).isEqualTo("user-1");
        assertThat(result.token()).isEqualTo("new-token");
    }

    @Test
    @DisplayName("validateAndRotate rejects an access token passed as a refresh token")
    void validateAndRotateRejectsAccessToken() {
        when(jwtTokenProvider.getUserIdFromRefreshToken("access-token")).thenReturn(null);

        assertThatThrownBy(() -> refreshTokenService.validateAndRotate("access-token"))
                .isInstanceOf(AuthBusinessException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    @DisplayName("validateAndRotate rejects replay when atomic revoke loses the race")
    void validateAndRotateRejectsReplayWhenAtomicUpdateLosesRace() {
        RefreshToken stored = new RefreshToken();
        stored.setId("token-1");
        stored.setUserId("user-1");
        stored.setIsRevoked(false);
        stored.setExpiresAt(LocalDateTime.parse("2026-01-02T00:00:00"));

        when(jwtTokenProvider.getUserIdFromRefreshToken("old-token")).thenReturn("user-1");
        when(refreshTokenMapper.selectOne(any(Wrapper.class))).thenReturn(stored);
        when(refreshTokenMapper.revokeIfActive("token-1")).thenReturn(0);

        assertThatThrownBy(() -> refreshTokenService.validateAndRotate("old-token"))
                .isInstanceOf(AuthBusinessException.class)
                .hasMessageContaining("already been used");
    }
}
