package com.ulticode.auth.adapter.in.web;

import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.service.OAuthLoginWorkflow;
import com.ulticode.auth.session.SessionCookieAdapter;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.response.Result;
import com.ulticode.websecurity.annotation.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OAuth HTTP adapter exposing provider-neutral third-party login endpoints.
 *
 * <p>The workflow owns provider lookup, state lifecycle, account linking, and
 * session issuance. This adapter only maps HTTP values and applies the cookie
 * mutations returned by the workflow.</p>
 */
@RestController
@RequestMapping("/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthLoginWorkflow oauthLoginWorkflow;
    private final SessionCookieAdapter sessionCookieAdapter;

    /**
     * Get the authorization URL for a configured OAuth provider.
     *
     * @param provider the OAuth provider name
     * @return the authorization URL to redirect the user to
     */
    @GetMapping("/{provider}/auth-url")
    @RateLimit(limit = 10, period = 60, key = "auth:oauth:authurl:ip:{ip}")
    public Result<Map<String, String>> getAuthUrl(
            @PathVariable @NotBlank String provider,
            HttpServletResponse response) {
        OAuthLoginWorkflow.OAuthAuthorization authorization = oauthLoginWorkflow.begin(normalizeProvider(provider));
        sessionCookieAdapter.applyCookies(List.of(authorization.stateCookie()), response);
        return Result.success(Map.of("authUrl", authorization.authorizationUrl()));
    }

    /**
     * Handle the OAuth callback from a provider.
     *
     * @param provider    the OAuth provider name
     * @param code        the authorization code returned by the provider
     * @param state       the state parameter returned by the provider
     * @return login response with access token and user info
     */
    @GetMapping("/{provider}/callback")
    @RateLimit(limit = 20, period = 60, key = "auth:oauth:callback:ip:{ip}")
    public Result<LoginResponse> callback(
            @PathVariable @NotBlank String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (code == null || code.isBlank()) {
            throw new AuthBusinessException(BaseErrorCode.BAD_REQUEST,
                    "OAuth authorization code is required");
        }

        String normalizedProvider = normalizeProvider(provider);
        String cookieState = extractStateCookie(normalizedProvider, request);

        try {
            OAuthLoginWorkflow.OAuthCompletion completion = oauthLoginWorkflow.complete(
                    normalizedProvider, code, state, cookieState);
            sessionCookieAdapter.applyCookies(completion.cookies(), response);
            return Result.success(completion.response());
        } catch (OAuthLoginWorkflow.OAuthCallbackFailure failure) {
            // The workflow has begun state validation or consumed state. Apply the
            // explicit cleanup before the original exception reaches the existing
            // AuthWebExceptionHandler and preserves its error envelope/status.
            sessionCookieAdapter.applyCookies(List.of(failure.stateCookie()), response);
            throw failure.cause();
        }
    }

    private String extractStateCookie(String provider, HttpServletRequest request) {
        if (request == null || request.getCookies() == null) {
            return null;
        }
        String cookieName = "oauth_state_" + provider;
        for (var cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String normalizeProvider(String provider) {
        return provider == null ? null : provider.toLowerCase(Locale.ROOT);
    }
}
