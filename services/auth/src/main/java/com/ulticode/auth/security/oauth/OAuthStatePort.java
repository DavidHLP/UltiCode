package com.ulticode.auth.security.oauth;

import com.ulticode.auth.session.CookieMutation;

import java.util.Objects;

/**
 * Port for the OAuth state lifecycle — security invariant #5.
 *
 * <p>The port owns state persistence and cookie policy, while the inbound HTTP
 * adapter applies the returned mutations.</p>
 */
public interface OAuthStatePort {

    OAuthStateIssue issueState(String provider);

    CookieMutation validateAndConsume(String provider, String state, String cookieState);

    CookieMutation clearStateCookie(String provider);

    /** State value plus the cookie mutation required to establish the browser binding. */
    record OAuthStateIssue(String state, CookieMutation stateCookie) {
        public OAuthStateIssue {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(stateCookie, "stateCookie");
        }
    }
}
