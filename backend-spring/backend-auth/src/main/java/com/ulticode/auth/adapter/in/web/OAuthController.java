package com.ulticode.auth.adapter.in.web;

import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.service.OAuthService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.response.Result;
import com.ulticode.websecurity.annotation.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * OAuth HTTP controller exposing third-party login endpoints.
 *
 * <p>AUTH-COMP-002: wires {@link OAuthService} to the HTTP surface so the
 * auth-url and callback endpoints are reachable through the Nginx
 * {@code /api/auth/} route. The service logic (state issuance, atomic
 * consume, provider client, account linking) is owned by
 * {@link OAuthService}; this controller is a thin adapter.
 */
@Slf4j
@RestController
@RequestMapping("/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oauthService;

    /**
     * Get the authorization URL for a provider (github or google).
     *
     * @param provider the OAuth provider name
     * @return the authorization URL to redirect the user to
     */
    @GetMapping("/{provider}/auth-url")
    @RateLimit(limit = 10, period = 60, key = "auth:oauth:authurl:ip:{ip}")
    public Result<Map<String, String>> getAuthUrl(
            @PathVariable @NotBlank String provider,
            HttpServletResponse response) {
        String url = switch (provider.toLowerCase()) {
            case "github" -> oauthService.getGithubAuthUrl(response);
            case "google" -> oauthService.getGoogleAuthUrl(response);
            default -> throw new AuthBusinessException(BaseErrorCode.BAD_REQUEST,
                    "Unsupported OAuth provider: " + provider);
        };
        return Result.success(Map.of("authUrl", url));
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

        String cookieState = extractStateCookie(provider, request);

        LoginResponse loginResponse = switch (provider.toLowerCase()) {
            case "github" -> oauthService.handleGithubCallback(code, state, cookieState, response);
            case "google" -> oauthService.handleGoogleCallback(code, state, cookieState, response);
            default -> throw new AuthBusinessException(BaseErrorCode.BAD_REQUEST,
                    "Unsupported OAuth provider: " + provider);
        };

        return Result.success(loginResponse);
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
}
