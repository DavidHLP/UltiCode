package com.ulticode.modules.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.config.CorsProperties;
import com.ulticode.common.config.MapperConfig;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.dto.LoginDTO;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.auth.service.AuthService;
import com.ulticode.modules.auth.service.OAuthService;
import com.ulticode.modules.auth.service.PasswordResetService;
import com.ulticode.modules.permission.service.PermissionService;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.projection.UserReadProjection;
import com.ulticode.security.AuthenticationEntryPointImpl;
import com.ulticode.security.csrf.CsrfService;
import com.ulticode.security.jwt.JwtAuthenticationFilter;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * @WebMvcTest for AuthController.
 *
 * <p>Uses addFilters=false to bypass all security filters (JwtAuthenticationFilter,
 * CsrfValidationFilter). This isolates the controller layer so we can test
 * request/response contracts without security infrastructure.</p>
 *
 * <p>Trade-offs:
 * <ul>
 *   <li>Login validation and error tests work without CSRF overhead</li>
 *   <li>/auth/me uses .with(user(...)) to inject Principal directly</li>
 *   <li>/auth/me without auth returns 500 (no security filter to return 401)</li>
 *   <li>Security enforcement is tested separately in integration tests</li>
 * </ul>
 * </p>
 */
@WebMvcTest(
        value = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = MapperConfig.class
        )
)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Service dependencies (what AuthController actually uses)
    @MockBean
    private AuthService authService;
    @MockBean
    private CsrfService csrfService;
    @MockBean
    private UserReadProjection userReadProjection;
    @MockBean
    private PasswordResetService passwordResetService;
    @MockBean
    private OAuthService oauthService;
    @MockBean
    private PermissionService permissionService;

    // SecurityConfig dependencies
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private JwtProperties jwtProperties;
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean
    private AuthenticationEntryPointImpl authenticationEntryPoint;
    @MockBean
    private CorsProperties corsProperties;
    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @Nested
    @DisplayName("POST /auth/login")
    class LoginTests {

        @Test
        @DisplayName("should return 200 with Result envelope on successful login")
        void login_success() throws Exception {
            UserVO userVO = new UserVO();
            userVO.setId("user-1");
            userVO.setUsername("testuser");

            LoginResponse loginResponse = LoginResponse.builder()
                    .csrfToken("csrf-token-123")
                    .user(userVO)
                    .build();

            when(authService.login(any(LoginDTO.class), any())).thenReturn(loginResponse);

            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername("testuser");
            loginDTO.setPassword("password123");

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.csrfToken").value("csrf-token-123"))
                    .andExpect(jsonPath("$.data.user.username").value("testuser"));
        }

        @Test
        @DisplayName("should return 400 when username is blank")
        void login_validationError_blankUsername() throws Exception {
            String json = "{\"username\":\"\",\"password\":\"pass\"}";

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("should return error when AuthService throws BusinessException")
        void login_unauthorized_badCredentials() throws Exception {
            when(authService.login(any(LoginDTO.class), any()))
                    .thenThrow(new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));

            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername("baduser");
            loginDTO.setPassword("wrongpass");

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginDTO)))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /auth/me")
    class GetCurrentUserTests {

        @Test
        @DisplayName("should return 200 with user data and csrfToken for authenticated user")
        void getCurrentUser_success() throws Exception {
            User user = new User();
            user.setId("user-1");
            user.setUsername("testuser");

            UserVO userVO = new UserVO();
            userVO.setId("user-1");
            userVO.setUsername("testuser");

            when(userReadProjection.findById("user-1")).thenReturn(Optional.of(user));
            when(userReadProjection.toVO(user)).thenReturn(userVO);
            when(csrfService.generateToken("user-1")).thenReturn("csrf-token");

            mockMvc.perform(get("/auth/me")
                            .with(request -> {
                                request.setUserPrincipal(() -> "user-1");
                                return request;
                            }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.user.id").value("user-1"))
                    .andExpect(jsonPath("$.data.csrfToken").value("csrf-token"));
        }

        @Test
        @DisplayName("should return 500 when no authentication is provided (Principal is null)")
        void getCurrentUser_unauthorized() throws Exception {
            // Without security filters, there is no authentication enforcement.
            // The controller calls principal.getName() which throws NullPointerException.
            mockMvc.perform(get("/auth/me"))
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    @DisplayName("POST /auth/logout")
    class LogoutTests {

        @Test
        @DisplayName("should return 200 with code=0 on successful logout")
        void logout_success() throws Exception {
            doNothing().when(authService).logout(any(), any());

            mockMvc.perform(post("/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }
}
