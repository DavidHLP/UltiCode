package com.ulticode.auth.service;

import com.ulticode.auth.account.AuthAccountPort;
import com.ulticode.auth.account.AuthAccountRecord;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.security.oauth.OAuthClient;
import com.ulticode.auth.security.oauth.OAuthProperties;
import com.ulticode.auth.security.oauth.OAuthStatePort;
import com.ulticode.auth.security.oauth.OAuthTokenResponse;
import com.ulticode.auth.security.oauth.OAuthUserInfo;
import com.ulticode.auth.security.oauth.entity.OAuthProviderIdentity;
import com.ulticode.auth.security.oauth.mapper.OAuthProviderIdentityMapper;
import com.ulticode.auth.session.AuthSession;
import com.ulticode.auth.session.AuthSessionPort;
import com.ulticode.auth.session.CookieMutation;
import com.ulticode.auth.util.UuidGenerator;
import com.ulticode.common.error.BaseErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Default provider-neutral implementation of {@link OAuthLoginWorkflow}. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultOAuthLoginWorkflow implements OAuthLoginWorkflow {

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
            String provider = normalizeProvider(client.getProviderName());
            OAuthClient previous = clientByName.put(provider, client);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate OAuthClient for provider '" + provider
                                + "': " + previous.getClass().getName()
                                + " and " + client.getClass().getName());
            }
        }
        log.info("DefaultOAuthLoginWorkflow wired with {} provider client(s): {}",
                clientByName.size(), clientByName.keySet());
    }

    @Override
    public OAuthAuthorization begin(String provider) {
        String normalizedProvider = normalizeProvider(provider);
        OAuthClient client = requireClient(normalizedProvider);
        String redirectUri = redirectUriFor(normalizedProvider);
        OAuthStatePort.OAuthStateIssue state = oauthStatePort.issueState(normalizedProvider);
        String authorizationUrl = client.buildAuthorizationUrl(state.state(), redirectUri);
        return new OAuthAuthorization(authorizationUrl, state.stateCookie());
    }

    @Override
    public OAuthCompletion complete(String provider, String code, String state, String cookieState) {
        String normalizedProvider = normalizeProvider(provider);
        OAuthClient client = requireClient(normalizedProvider);
        String redirectUri = redirectUriFor(normalizedProvider);
        CookieMutation clearStateCookie = oauthStatePort.clearStateCookie(normalizedProvider);

        try {
            CookieMutation consumedStateCookie = oauthStatePort.validateAndConsume(
                    normalizedProvider, state, cookieState);
            OAuthTokenResponse token = client.exchangeCodeForToken(code, redirectUri);
            OAuthUserInfo userInfo = client.fetchUserInfo(token.accessToken());
            AuthSession session = createOrUpdateUser(userInfo, normalizedProvider);

            List<CookieMutation> cookies = new ArrayList<>();
            cookies.add(consumedStateCookie == null ? clearStateCookie : consumedStateCookie);
            cookies.addAll(session.cookies());
            return new OAuthCompletion(session.response(), cookies);
        } catch (OAuthCallbackFailure failure) {
            throw failure;
        } catch (RuntimeException failure) {
            // State has been consumed or validation has been attempted. The HTTP
            // adapter must clear the browser binding before the original error is mapped.
            throw new OAuthCallbackFailure(failure, clearStateCookie);
        }
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
            default -> throw new AuthBusinessException(BaseErrorCode.BAD_REQUEST,
                    "Unsupported OAuth provider: " + provider);
        };
    }

    private AuthSession createOrUpdateUser(OAuthUserInfo userInfo, String provider) {
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
                return authSessionPort.completeLogin(bound);
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

        return authSessionPort.completeLogin(account);
    }

    private void linkProviderIdentity(String accountId, String provider, String providerUserId) {
        OAuthProviderIdentity identity = new OAuthProviderIdentity();
        identity.setId(uuidGenerator.newId());
        identity.setAccountId(accountId);
        identity.setProvider(provider);
        identity.setProviderUserId(providerUserId);
        try {
            providerIdentityMapper.insert(identity);
        } catch (DuplicateKeyException raceLost) {
            // UNIQUE(provider, providerUserId) may already exist from a concurrent
            // callback or a re-link after a race. The existing winner owns the binding.
            log.warn("Provider identity already linked, skipping insert: provider={}, providerUserId={}",
                    provider, providerUserId);
        }
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new AuthBusinessException(BaseErrorCode.BAD_REQUEST, "Unsupported OAuth provider: " + provider);
        }
        return provider.toLowerCase(Locale.ROOT);
    }
}
