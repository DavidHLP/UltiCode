package com.ulticode.modules.auth.service;

import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.user.dto.UserVO;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.user.service.UserService;
import com.ulticode.modules.refreshtoken.service.RefreshTokenService;
import com.ulticode.security.csrf.CsrfService;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.jwt.JwtTokenProvider;
import com.ulticode.security.oauth.OAuthProperties;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

/**
 * OAuth service for handling third-party authentication (GitHub and Google).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final OAuthProperties oauthProperties;
    private final UserMapper userMapper;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenService refreshTokenService;
    private final CsrfService csrfService;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final StringRedisTemplate redisTemplate;

    private static final String OAUTH_STATE_PREFIX = "oauth:state:";
    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(5);

    // ==================== GitHub OAuth ====================

    /**
     * Generate GitHub OAuth authorization URL.
     *
     * @return the authorization URL
     */
    public String getGithubAuthUrl() {
        OAuthProperties.OAuthProvider github = oauthProperties.getGithub();
        String state = IdUtil.simpleUUID();
        redisTemplate.opsForValue().set(OAUTH_STATE_PREFIX + "github:" + state, "1", OAUTH_STATE_TTL);

        return UriComponentsBuilder.fromHttpUrl(github.getAuthorizeUrl())
            .queryParam("client_id", github.getClientId())
            .queryParam("redirect_uri", github.getRedirectUri())
            .queryParam("scope", github.getScopes())
            .queryParam("state", state)
            .toUriString();
    }

    /**
     * Handle GitHub OAuth callback.
     *
     * @param code     the authorization code
     * @param state    the OAuth state parameter for CSRF validation
     * @param response the HTTP response
     * @return login response with tokens and user info
     */
    public LoginResponse handleGithubCallback(String code, String state, HttpServletResponse response) {
        validateOAuthState("github", state);
        OAuthProperties.OAuthProvider github = oauthProperties.getGithub();

        // Use RFC 6749 Basic Auth instead of plaintext body
        String basicAuth = Base64.getEncoder().encodeToString(
            (github.getClientId() + ":" + github.getClientSecret()).getBytes(StandardCharsets.UTF_8));

        String tokenResponse;
        try (HttpResponse resp = HttpRequest.post(github.getTokenUrl())
            .header("Accept", "application/json")
            .header("Authorization", "Basic " + basicAuth)
            .body("code=" + code +
                  "&redirect_uri=" + URLEncoder.encode(github.getRedirectUri(), StandardCharsets.UTF_8))
            .execute()) {
            tokenResponse = resp.body();
        }

        String accessToken;
        try {
            JsonNode tokenNode = objectMapper.readTree(tokenResponse);
            accessToken = tokenNode.get("access_token").asText();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse GitHub token response", e);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "OAuth token exchange failed", e);
        }

        // 获取用户信息
        String userResponse;
        try (HttpResponse resp = HttpRequest.get(github.getUserUrl())
            .header("Authorization", "Bearer " + accessToken)
            .header("Accept", "application/json")
            .execute()) {
            userResponse = resp.body();
        }

        try {
            JsonNode userNode = objectMapper.readTree(userResponse);
            String githubId = userNode.get("id").asText();
            String login = userNode.get("login").asText();
            String email = userNode.has("email") && !userNode.get("email").isNull()
                ? userNode.get("email").asText()
                : login + "@github";
            String avatar = userNode.has("avatar_url") ? userNode.get("avatar_url").asText() : null;

            return createOrUpdateUser(githubId, login, email, avatar, "github", response);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse GitHub user response", e);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Failed to get GitHub user info", e);
        }
    }

    // ==================== Google OAuth ====================

    /**
     * Generate Google OAuth authorization URL.
     *
     * @return the authorization URL
     */
    public String getGoogleAuthUrl() {
        OAuthProperties.OAuthProvider google = oauthProperties.getGoogle();
        String state = IdUtil.simpleUUID();
        redisTemplate.opsForValue().set(OAUTH_STATE_PREFIX + "google:" + state, "1", OAUTH_STATE_TTL);

        return UriComponentsBuilder.fromHttpUrl(google.getAuthorizeUrl())
            .queryParam("client_id", google.getClientId())
            .queryParam("redirect_uri", google.getRedirectUri())
            .queryParam("response_type", "code")
            .queryParam("scope", google.getScopes())
            .queryParam("state", state)
            .toUriString();
    }

    /**
     * Handle Google OAuth callback.
     *
     * @param code     the authorization code
     * @param state    the OAuth state parameter for CSRF validation
     * @param response the HTTP response
     * @return login response with tokens and user info
     */
    public LoginResponse handleGoogleCallback(String code, String state, HttpServletResponse response) {
        validateOAuthState("google", state);
        OAuthProperties.OAuthProvider google = oauthProperties.getGoogle();

        // Use RFC 6749 Basic Auth instead of plaintext body
        String basicAuth = Base64.getEncoder().encodeToString(
            (google.getClientId() + ":" + google.getClientSecret()).getBytes(StandardCharsets.UTF_8));

        String tokenRequestBody = "code=" + code +
              "&redirect_uri=" + URLEncoder.encode(google.getRedirectUri(), StandardCharsets.UTF_8) +
              "&grant_type=authorization_code";

        String tokenResponse;
        try (HttpResponse resp = HttpRequest.post(google.getTokenUrl())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Authorization", "Basic " + basicAuth)
            .body(tokenRequestBody)
            .execute()) {
            tokenResponse = resp.body();
        }

        String accessToken;
        try {
            JsonNode tokenNode = objectMapper.readTree(tokenResponse);
            accessToken = tokenNode.get("access_token").asText();
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Google token response", e);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "OAuth token exchange failed", e);
        }

        // 获取用户信息
        String userResponse;
        try (HttpResponse resp = HttpRequest.get(google.getUserUrl())
            .header("Authorization", "Bearer " + accessToken)
            .execute()) {
            userResponse = resp.body();
        }

        try {
            JsonNode userNode = objectMapper.readTree(userResponse);
            String googleId = userNode.get("id").asText();
            String email = userNode.get("email").asText();
            String name = userNode.has("name") ? userNode.get("name").asText() : email.split("@")[0];
            String avatar = userNode.has("picture") ? userNode.get("picture").asText() : null;

            return createOrUpdateUser(googleId, name, email, avatar, "google", response);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Google user response", e);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Failed to get Google user info", e);
        }
    }

    // ==================== 用户创建/更新 ====================

    private void validateOAuthState(String provider, String state) {
        if (state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "OAuth state parameter is missing");
        }
        String key = OAUTH_STATE_PREFIX + provider + ":" + state;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.FALSE.equals(exists)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid or expired OAuth state parameter");
        }
        redisTemplate.delete(key);
    }

    /**
     * Create or update a user from OAuth provider data.
     *
     * @param oauthId  the OAuth provider's user ID
     * @param name     the user's display name
     * @param email    the user's email address
     * @param avatar   the user's avatar URL
     * @param provider the OAuth provider name
     * @param response the HTTP response
     * @return login response with tokens and user info
     */
    private LoginResponse createOrUpdateUser(String oauthId, String name, String email,
                                              String avatar, String provider, HttpServletResponse response) {
        // 查找现有用户（通过 email）
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
        );

        if (user == null) {
            // 创建新用户
            user = new User();
            user.setId(IdUtil.fastSimpleUUID());
            user.setUsername(provider + "_" + oauthId);
            user.setName(name);
            user.setEmail(email);
            user.setAvatar(avatar);
            user.setRole("USER");
            user.setIsActive(true);
            user.setIsBanned(false);
            user.setJoinedAt(LocalDateTime.now());
            userMapper.insert(user);
            log.info("Created new user via {} OAuth: {}", provider, email);
        } else {
            // 更新头像（如果需要）
            if (avatar != null && !avatar.equals(user.getAvatar())) {
                user.setAvatar(avatar);
                userMapper.updateById(user);
            }
        }

        // 生成 JWT
        String jwtToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = refreshTokenService.createToken(user.getId(), response);

        // 设置 Cookie
        setAuthCookie(response, jwtToken);
        setRefreshTokenCookie(response, refreshToken);

        // 生成 CSRF Token
        String csrfToken = csrfService.generateToken(user.getId());

        UserVO userVO = userService.toVO(user);
        return LoginResponse.builder()
            .csrfToken(csrfToken)
            .user(userVO)
            .build();
    }

    private void setAuthCookie(HttpServletResponse response, String token) {
        JwtProperties.AccessTokenCookie config = jwtProperties.getCookie().getAccessToken();
        String headerValue = String.format("%s=%s; Path=%s; Max-Age=%d; HttpOnly%s; SameSite=%s",
            config.getName(), token, config.getPath(), config.getMaxAge(),
            config.isSecure() ? "; Secure" : "", config.getSameSite());
        response.addHeader("Set-Cookie", headerValue);
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String token) {
        JwtProperties.RefreshTokenCookie config = jwtProperties.getCookie().getRefreshToken();
        String headerValue = String.format("%s=%s; Path=%s; Max-Age=%d; HttpOnly%s; SameSite=%s",
            config.getName(), token, config.getPath(), config.getMaxAge(),
            config.isSecure() ? "; Secure" : "", config.getSameSite());
        response.addHeader("Set-Cookie", headerValue);
    }
}
