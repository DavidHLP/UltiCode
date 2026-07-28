package com.ulticode.modules.auth.service;

import cn.hutool.core.util.IdUtil;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.modules.auth.dto.LoginResponse;
import com.ulticode.modules.auth.session.AuthSessionPort;
import com.ulticode.modules.auth.session.OAuthStatePort;
import com.ulticode.modules.auth.service.oauth.OAuthClient;
import com.ulticode.modules.auth.service.oauth.OAuthUserInfo;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.port.UserProfilePort;
import com.ulticode.modules.auth.account.AuthAccountPort;
import com.ulticode.security.oauth.OAuthProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Coordinator for third-party OAuth login (GitHub and Google).
 *
 * <p>After the port-extraction refactor, the provider-specific concerns
 * — authorization URL construction, Basic-Auth header on the token
 * endpoint, token- and user-info JSON parsing — live behind
 * {@link OAuthClient} adapters ({@code GithubOAuthClient},
 * {@code GoogleOAuthClient}). This coordinator only:
 * <ol>
 *   <li>issues / atomically consumes the OAuth state via
 *       {@link OAuthStatePort} (security invariant #5);</li>
 *   <li>picks the matching {@link OAuthClient} by
 *       {@link OAuthClient#getProviderName()};</li>
 *   <li>runs the DB upsert tail (find by email, create-or-update avatar)
 *       and delegates the post-auth session wiring to
 *       {@link AuthSessionPort}.</li>
 * </ol>
 *
 * <p>The public callback API
 * ({@code handleGithubCallback}, {@code handleGoogleCallback}) carries the
 * callback's {@code state} query parameter AND the value of the
 * {@code oauth_state_<provider>} HttpOnly cookie. The cookie-binding
 * constant-time compare lives inside {@link OAuthStatePort#validateAndConsume};
 * this coordinator only forwards both values and lets the port enforce the
 * invariant. AuthController extracts the cookie from the request and threads
 * it here.
 *
 * <p><strong>Phase 0 / MICROSERVICE_MIGRATION_GUIDE.md §7.1:</strong>
 * {@link #createOrUpdateUser} refuses to auto-link an OAuth identity to
 * an existing account by email when {@code OAuthUserInfo.emailVerified()}
 * is {@code false}. This is the canonical "wrong-account merge" defense
 * (R4 in §12). Brand-new accounts (no match) are still allowed; the email
 * column stays null until a verified login establishes one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final OAuthProperties oauthProperties;
    private final AuthAccountPort accountPort;
    private final UserProfilePort userProfilePort;
    private final AuthSessionPort authSessionPort;
    private final OAuthStatePort oauthStatePort;
    private final List<OAuthClient> oauthClients;
    private final Clock clock;

    /**
     * Pre-computed lookup from provider name ({@code "github"} /
     * {@code "google"}) to the matching {@link OAuthClient} bean. Built
     * once at {@code @PostConstruct} so each callback avoids a linear scan
     * and duplicate beans fail fast.
     */
    private final Map<String, OAuthClient> clientByName = new HashMap<>();

    /**
     * Build the client-by-name map from the autowired list. Spring invokes
     * this after dependency injection; tests that wire mocks manually must
     * call it too.
     */
    @PostConstruct
    void wireClientLookup() {
        clientByName.clear();
        for (OAuthClient client : oauthClients) {
            OAuthClient previous = clientByName.put(client.getProviderName(), client);
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate OAuthClient for provider '" + client.getProviderName()
                        + "': " + previous.getClass().getName()
                        + " and " + client.getClass().getName());
            }
        }
        log.info("OAuthService wired with {} provider client(s): {}",
            clientByName.size(), clientByName.keySet());
    }

    // ==================== GitHub OAuth ====================

    public String getGithubAuthUrl(HttpServletResponse response) {
        return buildAuthUrl("github", response);
    }

    public LoginResponse handleGithubCallback(String code, String state,
                                              String cookieState, HttpServletResponse response) {
        return handleCallback("github", code, state, cookieState, response);
    }

    // ==================== Google OAuth ====================

    public String getGoogleAuthUrl(HttpServletResponse response) {
        return buildAuthUrl("google", response);
    }

    public LoginResponse handleGoogleCallback(String code, String state,
                                              String cookieState, HttpServletResponse response) {
        return handleCallback("google", code, state, cookieState, response);
    }

    // ==================== Coordinator internals ====================

    private String buildAuthUrl(String provider, HttpServletResponse response) {
        String state = oauthStatePort.issueState(provider, response);
        OAuthClient client = requireClient(provider);
        String redirectUri = redirectUriFor(provider);
        return client.buildAuthorizationUrl(state, redirectUri);
    }

    private LoginResponse handleCallback(String provider, String code, String state,
                                         String cookieState, HttpServletResponse response) {
        // Security invariant #5: bind the callback state to the browser's
        // oauth_state_<provider> cookie via constant-time compare inside the
        // module, then atomically consume the Redis entry. Throws on blank
        // state, cookie/state mismatch, or unknown/expired/replayed state.
        // The state contract is cross-provider, not GitHub- or Google-
        // specific, so it stays in the port rather than each OAuthClient.
        oauthStatePort.validateAndConsume(provider, state, cookieState, response);

        OAuthClient client = requireClient(provider);
        String redirectUri = redirectUriFor(provider);

        var token = client.exchangeCodeForToken(code, redirectUri);
        OAuthUserInfo userInfo = client.fetchUserInfo(token.accessToken());

        return createOrUpdateUser(userInfo, provider, response);
    }

    private OAuthClient requireClient(String provider) {
        OAuthClient client = clientByName.get(provider);
        if (client == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                "Unsupported OAuth provider: " + provider);
        }
        return client;
    }

    private String redirectUriFor(String provider) {
        return switch (provider) {
            case "github" -> oauthProperties.getGithub().getRedirectUri();
            case "google" -> oauthProperties.getGoogle().getRedirectUri();
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST,
                "Unsupported OAuth provider: " + provider);
        };
    }

    /**
     * Create or update a user from the normalized provider user-info.
     *
     * <p><strong>Phase 0 §7.1:</strong> if the email matches an existing
     * account but the provider did NOT verify the email
     * ({@code OAuthUserInfo.emailVerified()} is {@code false}), the
     * auto-link is refused — see R4 in the guide §12.
     *
     * <p>The post-auth tail (cookies, CSRF, JWT, {@code LoginResponse})
     * is delegated to {@link AuthSessionPort#completeLogin}.
     */
    private LoginResponse createOrUpdateUser(OAuthUserInfo userInfo, String provider,
                                             HttpServletResponse response) {
        Optional<User> existingByEmail = accountPort.findByOAuthEmail(userInfo.email());
        if (existingByEmail.isPresent() && !userInfo.emailVerified()) {
            log.warn("Refusing OAuth auto-link by unverified email: provider={}, userId={}",
                provider, existingByEmail.get().getId());
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS,
                "OAuth email is not verified; cannot link to existing account");
        }
        User user = existingByEmail.orElse(null);

        if (user == null) {
            // Create a brand-new user. The email may be null when the
            // provider did not expose a verified one; in that case the
            // account is created with email=null and the user is asked
            // to add one through profile settings.
            user = new User();
            user.setId(IdUtil.fastSimpleUUID());
            user.setUsername(provider + "_" + userInfo.providerId());
            user.setName(userInfo.name());
            user.setEmail(userInfo.email());
            user.setAvatar(userInfo.avatar());
            user.setRole("USER");
            user.setIsActive(true);
            user.setIsBanned(false);
            user.setJoinedAt(LocalDateTime.now(clock));
            accountPort.create(user);
            log.info("Created new user via {} OAuth: {}", provider, userInfo.email());
        } else {
            // 更新头像（如果需要）
            String avatar = userInfo.avatar();
            if (avatar != null && !avatar.equals(user.getAvatar())) {
                userProfilePort.updateAvatarUrl(user.getId(), avatar);
            }
        }

        return authSessionPort.completeLogin(user, response);
    }
}