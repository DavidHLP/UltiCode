package com.ulticode.auth.service;

import com.ulticode.auth.dto.LoginResponse;
import com.ulticode.auth.session.CookieMutation;

import java.util.List;
import java.util.Objects;

/**
 * Provider-neutral OAuth login workflow owned by backend-auth.
 *
 * <p>The HTTP adapter supplies request values and applies the returned cookie
 * mutations. Neither operation accepts a servlet request or response.</p>
 */
public interface OAuthLoginWorkflow {

    OAuthAuthorization begin(String provider);

    OAuthCompletion complete(String provider, String code, String state, String cookieState);

    /** Authorization URL plus the state cookie that must be set by the HTTP adapter. */
    record OAuthAuthorization(String authorizationUrl, CookieMutation stateCookie) {
        public OAuthAuthorization {
            Objects.requireNonNull(authorizationUrl, "authorizationUrl");
            Objects.requireNonNull(stateCookie, "stateCookie");
        }
    }

    /** Login response plus all cookie mutations that must be applied by the HTTP adapter. */
    record OAuthCompletion(LoginResponse response, List<CookieMutation> cookies) {
        public OAuthCompletion {
            cookies = cookies == null ? List.of() : List.copyOf(cookies);
        }
    }

    /**
     * Signals a callback failure after state processing has begun and carries
     * the state-cookie deletion required before the original failure is mapped
     * by the existing auth exception handler.
     */
    final class OAuthCallbackFailure extends RuntimeException {
        private final CookieMutation stateCookie;

        public OAuthCallbackFailure(RuntimeException cause, CookieMutation stateCookie) {
            super(Objects.requireNonNull(cause, "cause"));
            this.stateCookie = Objects.requireNonNull(stateCookie, "stateCookie");
        }

        public CookieMutation stateCookie() {
            return stateCookie;
        }

        public RuntimeException cause() {
            return (RuntimeException) getCause();
        }
    }
}
