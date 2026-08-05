package com.ulticode.auth.service;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.security.oauth.OAuthClient;
import com.ulticode.auth.security.oauth.OAuthProperties;
import com.ulticode.auth.security.oauth.OAuthStatePort;
import com.ulticode.auth.security.oauth.OAuthTokenResponse;
import com.ulticode.auth.security.oauth.OAuthUserInfo;
import com.ulticode.auth.security.oauth.entity.OAuthProviderIdentity;
import com.ulticode.auth.security.oauth.mapper.OAuthProviderIdentityMapper;
import com.ulticode.auth.session.AuthSessionPort;
import com.ulticode.auth.util.UuidGenerator;
import com.ulticode.common.error.BaseErrorCode;
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
 * Coordinator for third-party OAuth login (GitHub and Google) inside backend-auth.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final OAuthProperties oauthProperties;
    private final AuthAccountPort accountPort;
    private final AuthSessionPort authSessionPort;
    private final OAuthStatePort oauthStatePort;
    private final List<OAuthClient> oauthClients;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;
    private final OAuthProviderIdentityMapper providerIdentityMapper;

    private final Map<String, OAuthClient> clientByName = new HashMap<>();

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

    public String getGithubAuthUrl(HttpServletResponse response) {
        return buildAuthUrl("github", response);
    }

    public LoginResponse handleGithubCallback(String code, String state,
                                              String cookieState, HttpServletResponse response) {
        return handleCallback("github", code, state, cookieState, response);
    }

    public String getGoogleAuthUrl(HttpServletResponse response) {
        return buildAuthUrl("google", response);
    }

    public LoginResponse handleGoogleCallback(String code, String state,
                                              String cookieState, HttpServletResponse response) {
        return handleCallback("google", code, state, cookieState, response);
    }

    private String buildAuthUrl(String provider, HttpServletResponse response) {
        String state = oauthStatePort.issueState(provider, response);
        OAuthClient client = requireClient(provider);
        String redirectUri = redirectUriFor(provider);
        return client.buildAuthorizationUrl(state, redirectUri);
    }

    private LoginResponse handleCallback(String provider, String code, String state,
                                         String cookieState, HttpServletResponse response) {
        oauthStatePort.validateAndConsume(provider, state, cookieState, response);

        OAuthClient client = requireClient(provider);
        String redirectUri = redirectUriFor(provider);

        OAuthTokenResponse token = client.exchangeCodeForToken(code, redirectUri);
        OAuthUserInfo userInfo = client.fetchUserInfo(token.accessToken());

        return createOrUpdateUser(userInfo, provider, response);
    }

    private OAuthClient requireClient(String provider) {
        OAuthClient client = clientByName.get(provider);
        if (client == null) {
            throw new AuthBusinessException(BaseErrorCode.BAD_REQUEST, "Unsupported OAuth provider: " + provider);
        }
        return client;
    }

    private String redirectUriFor(String provider) {
        return switch (provider) {
            case "github" -> oauthProperties.getGithub().getRedirectUri();
            case "google" -> oauthProperties.getGoogle().getRedirectUri();
            default -> throw new AuthBusinessException(BaseErrorCode.BAD_REQUEST, "Unsupported OAuth provider: " + provider);
        };
    }

    private LoginResponse createOrUpdateUser(OAuthUserInfo userInfo, String provider,
                                             HttpServletResponse response) {
        // AUTH-COMP-004: look up by (provider, providerUserId) first — this is the
        // authoritative binding. Email is only a fallback for legacy accounts that
        // were linked before oauth_provider_identities existed.
        OAuthProviderIdentity existingBinding = providerIdentityMapper
                .findActiveByProviderAndProviderUserId(provider, userInfo.providerId());
        if (existingBinding != null) {
            AuthAccountRecord bound = accountPort.findById(existingBinding.getAccountId()).orElse(null);
            if (bound != null) {
                log.info("OAuth login via existing provider binding: provider={}, accountId={}",
                        provider, bound.id());
                return authSessionPort.completeLogin(bound, response);
            }
            log.warn("Provider identity points to deleted account: provider={}, accountId={}",
                    provider, existingBinding.getAccountId());
        }

        // Fallback: try email-based lookup (legacy path). Only allow for verified
        // emails to prevent wrong-account-merge (R4).
        Optional<AuthAccountRecord> existingByEmail = accountPort.findByEmail(userInfo.email());
        if (existingByEmail.isPresent() && !userInfo.emailVerified()) {
            log.warn("Refusing OAuth auto-link by unverified email: provider={}, userId={}",
                    provider, existingByEmail.get().id());
            throw new AuthBusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS,
                    "OAuth email is not verified; cannot link to existing account");
        }
        AuthAccountRecord account = existingByEmail.orElse(null);

        if (account == null) {
            String username = (userInfo.username() != null && !userInfo.username().isBlank())
                    ? userInfo.username()
                    : provider + "_" + userInfo.providerId();
            account = new AuthAccountRecord(
                    uuidGenerator.newId(),
                    username,
                    userInfo.email(),
                    "",
                    "USER",
                    true,
                    false,
                    null,
                    LocalDateTime.now(clock)
            );
            account = accountPort.create(account);
            log.info("Created new user via {} OAuth: {}", provider, userInfo.email());
        }

        // Write the provider identity binding so future logins use the authoritative
        // path instead of email.
        linkProviderIdentity(account.id(), provider, userInfo.providerId());

        return authSessionPort.completeLogin(account, response);
    }

    private void linkProviderIdentity(String accountId, String provider, String providerUserId) {
        OAuthProviderIdentity identity = new OAuthProviderIdentity();
        identity.setId(uuidGenerator.newId());
        identity.setAccountId(accountId);
        identity.setProvider(provider);
        identity.setProviderUserId(providerUserId);
        try {
            providerIdentityMapper.insert(identity);
        } catch (Exception e) {
            // UNIQUE(provider, providerUserId) may already exist from a concurrent
            // callback or a re-link after a race. Log and continue — the binding is
            // already there and the user can log in.
            log.warn("Provider identity already linked, skipping insert: provider={}, providerUserId={}",
                    provider, providerUserId);
        }
    }
}
