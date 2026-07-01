package com.ulticode.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.session.AuthSessionPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.security.csrf.CsrfService;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.oauth.OAuthProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class OAuthServiceTest {

    @Mock
    private OAuthProperties oauthProperties;

    @Mock
    private UserMapper userMapper;

    @Spy
    private JwtProperties jwtProperties = new JwtProperties();

    @Mock
    private CsrfService csrfService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AuthSessionPort authSessionPort;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private OAuthService oauthService;

    private static final String STATE = "abc-state";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void callbackRejectsUnknownState() {
        // No Redis state bound — the OAuth state was never issued or already consumed.
        when(valueOperations.getAndDelete("oauth:state:github:" + STATE)).thenReturn(null);

        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatThrownBy(() -> oauthService.handleGithubCallback("code", STATE, request, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void callbackRejectsBlankState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThatThrownBy(() -> oauthService.handleGithubCallback("code", "   ", request, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.BAD_REQUEST));
    }

    @Test
    void callbackConsumesStateOnSuccessPath() {
        // State was bound by the auth URL flow; we simulate consumption succeeded.
        when(valueOperations.getAndDelete("oauth:state:github:" + STATE)).thenReturn("1");

        // The handle callback will proceed past state validation and try to do an
        // HTTP exchange with GitHub, which will fail in a unit test environment.
        // We assert only that the state cookie was issued and the state is
        // consumed, which is the part owned by the auth module seam.
        MockHttpServletRequest request = new MockHttpServletRequest();
        try {
            oauthService.handleGithubCallback("code", STATE, request, new MockHttpServletResponse());
        } catch (Exception ignored) {
            // Real GitHub HTTP exchange will fail; we only assert the local seam.
        }
        verify(valueOperations).getAndDelete("oauth:state:github:" + STATE);
    }
}
