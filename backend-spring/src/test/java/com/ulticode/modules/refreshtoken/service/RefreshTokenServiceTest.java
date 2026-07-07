package com.ulticode.modules.refreshtoken.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.refreshtoken.entity.RefreshToken;
import com.ulticode.modules.refreshtoken.mapper.RefreshTokenMapper;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import java.time.Clock;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock private RefreshTokenMapper refreshTokenMapper;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Spy private JwtProperties jwtProperties = new JwtProperties();
  @Mock private Clock clock;
  @InjectMocks private RefreshTokenService refreshTokenService;

  @Test
  void createTokenStoresOnlyHash() {
    when(jwtTokenProvider.generateRefreshToken("user-1")).thenReturn("refresh-jwt");

    assertThat(refreshTokenService.createToken("user-1")).isEqualTo("refresh-jwt");

    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenMapper).insert(captor.capture());
    assertThat(captor.getValue().getTokenHash()).hasSize(64);
  }

  @Test
  void validateAndRotateAtomicallyRevokesAndReissues() {
    when(clock.instant()).thenReturn(java.time.Instant.parse("2026-01-01T00:00:00Z"));
    when(clock.getZone()).thenReturn(java.time.ZoneId.of("UTC"));

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
  void validateAndRotateRejectsAccessToken() {
    when(jwtTokenProvider.getUserIdFromRefreshToken("access-token")).thenReturn(null);

    assertThatThrownBy(() -> refreshTokenService.validateAndRotate("access-token"))
        .isInstanceOf(BusinessException.class);
  }

  @Test
  void validateAndRotateRejectsReplayWhenAtomicUpdateLosesRace() {
    when(clock.instant()).thenReturn(java.time.Instant.parse("2026-01-01T00:00:00Z"));
    when(clock.getZone()).thenReturn(java.time.ZoneId.of("UTC"));

    RefreshToken stored = new RefreshToken();
    stored.setId("token-1");
    stored.setUserId("user-1");
    stored.setIsRevoked(false);
    stored.setExpiresAt(LocalDateTime.parse("2026-01-02T00:00:00"));

    when(jwtTokenProvider.getUserIdFromRefreshToken("old-token")).thenReturn("user-1");
    when(refreshTokenMapper.selectOne(any(Wrapper.class))).thenReturn(stored);
    when(refreshTokenMapper.revokeIfActive("token-1")).thenReturn(0);

    assertThatThrownBy(() -> refreshTokenService.validateAndRotate("old-token"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("already been used");
  }
}
