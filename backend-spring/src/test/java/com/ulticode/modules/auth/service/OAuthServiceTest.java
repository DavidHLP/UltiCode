package com.ulticode.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.modules.refreshtoken.service.RefreshTokenService;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.security.csrf.CsrfService;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import com.ulticode.security.oauth.OAuthProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

  @Mock private OAuthProperties oauthProperties;
  @Mock private UserMapper userMapper;
  @Mock private JwtTokenProvider jwtTokenProvider;
  @Mock private JwtProperties jwtProperties;
  @Mock private RefreshTokenService refreshTokenService;
  @Mock private CsrfService csrfService;
  @Mock private ObjectMapper objectMapper;
  @Mock private UserService userService;
  @Mock private StringRedisTemplate redisTemplate;
  @InjectMocks private OAuthService oauthService;

  @Test
  void callbackRejectsStateFromAnotherBrowser() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new MockCookie("oauth_state_github", "browser-a-state"));

    assertThatThrownBy(
            () ->
                oauthService.handleGithubCallback(
                    "code", "browser-b-state", request, new MockHttpServletResponse()))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("not bound to this browser");
  }
}
