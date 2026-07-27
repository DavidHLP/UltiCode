package com.ulticode.auth.security.oauth;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Port for the OAuth state lifecycle — security invariant #5.
 */
public interface OAuthStatePort {

    String issueState(String provider, HttpServletResponse response);

    void validateAndConsume(String provider, String state, String cookieState, HttpServletResponse response);
}
