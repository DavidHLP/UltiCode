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
import com.ulticode.modules.auth.session.AuthSessionPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.security.csrf.CsrfService;
import com.ulticode.security.jwt.JwtProperties;
import com.ulticode.security.oauth.OAuthProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
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
 *
 * <p>Provider-specific concerns (URL build, token exchange, user-info fetch,
 * upsert) live here. The post-auth tail (cookies, CSRF, JWT, LoginResponse)
 * is delegated to the deep {@link AuthSessionPort} so it does not drift from
 * the local-credentials path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final OAuthProperties oauthProperties;
    private final UserMapper userMapper;
    private final JwtProperties jwtProperties;
    private final CsrfService csrfService;
    private final ObjectMapper objectMapper;
    private final AuthSessionPort authSessionPort;
    private final StringRedisTemplate redisTemplate;

    private static final String OAUTH_STATE_PREFIX = "oauth:state:";
    private static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(5);

    // ==================== GitHub OAuth ====================

    public String getGithubAuthUrl(HttpServletResponse response) {
        OAuthProperties.OAuthProvider github = oauthProperties.getGithub();
        String state = IdUtil.simpleUUID();
        redisTemplate.opsForValue().set(OAUTH_STATE_PREFIX + "github:" + state, "1", OAUTH_STATE_TTL);
        setOAuthStateCookie("github", state, response);

        return UriComponentsBuilder.fromUriString(github.getAuthorizeUrl())
            .queryParam("client_id", github.getClientId())
            .queryParam("redirect_uri", github.getRedirectUri())
            .queryParam("scope", github.getScopes())
            .queryParam("state", state)
            .toUriString();
    }

    public LoginResponse handleGithubCallback(String code, String state, HttpServletRequest request,
                                              HttpServletResponse response) {
        validateOAuthState("github", state, request, response);
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
            .execute()) {
            userResponse = resp.body();
        }

        try {
            JsonNode userNode = objectMapper.readTree(userResponse);
            String githubId = userNode.get("id").asText();
            String name = userNode.has("name") ? userNode.get("name").asText() : userNode.get("login").asText();
            String email = userNode.has("email") && !userNode.get("email").isNull()
                    ? userNode.get("email").asText() : null;
            String avatar = userNode.has("avatar_url") ? userNode.get("avatar_url").asText() : null;

            return createOrUpdateUser(githubId, name, email, avatar, "github", response);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse GitHub user response", e);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Failed to get GitHub user info", e);
        }
    }

    // ==================== Google OAuth ====================

    public String getGoogleAuthUrl(HttpServletResponse response) {
        OAuthProperties.OAuthProvider google = oauthProperties.getGoogle();
        String state = IdUtil.simpleUUID();
        redisTemplate.opsForValue().set(OAUTH_STATE_PREFIX + "google:" + state, "1", OAUTH_STATE_TTL);
        setOAuthStateCookie("google", state, response);

        return UriComponentsBuilder.fromUriString(google.getAuthorizeUrl())
            .queryParam("client_id", google.getClientId())
            .queryParam("redirect_uri", google.getRedirectUri())
            .queryParam("response_type", "code")
            .queryParam("scope", google.getScopes())
            .queryParam("state", state)
            .toUriString();
    }

    public LoginResponse handleGoogleCallback(String code, String state, HttpServletRequest request,
                                              HttpServletResponse response) {
        validateOAuthState("google", state, request, response);
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

    // ==================== State validation ====================

    private void validateOAuthState(String provider, String state, HttpServletRequest request,
                                    HttpServletResponse response) {
        if (state == null || state.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "OAuth state parameter is missing");
        }
        String key = OAUTH_STATE_PREFIX + provider + ":" + state;
        String consumed = redisTemplate.opsForValue().getAndDelete(key);
        clearOAuthStateCookie(provider, response);
        if (consumed == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid or expired OAuth state parameter");
        }
    }

    /**
     * Create or update a user from OAuth provider data.
     *
     * <p>Provider-specific upsert. The post-auth tail (cookies, CSRF, JWT,
     * LoginResponse) is delegated to {@link AuthSessionPort#completeLogin}.
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

        return authSessionPort.completeLogin(user, response);
    }

    // ==================== State cookie helpers ====================

    private void setOAuthStateCookie(String provider, String state, HttpServletResponse response) {
        response.addHeader("Set-Cookie", String.format(
            "%s=%s; Path=/auth; Max-Age=%d; HttpOnly%s; SameSite=Lax",
            oauthStateCookieName(provider), state, OAUTH_STATE_TTL.toSeconds(),
            jwtProperties.getCookie().getAccessToken().isSecure() ? "; Secure" : ""));
    }

    private void clearOAuthStateCookie(String provider, HttpServletResponse response) {
        response.addHeader("Set-Cookie", String.format(
            "%s=; Path=/auth; Max-Age=0; HttpOnly%s; SameSite=Lax",
            oauthStateCookieName(provider),
            jwtProperties.getCookie().getAccessToken().isSecure() ? "; Secure" : ""));
    }

    private String oauthStateCookieName(String provider) {
        return "oauth_state_" + provider;
    }

    private String getCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
