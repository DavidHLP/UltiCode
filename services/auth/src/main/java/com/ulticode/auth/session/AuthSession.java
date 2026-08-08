package com.ulticode.auth.session;

import com.ulticode.auth.dto.LoginResponse;

import java.util.List;

/**
 * Authentication result plus the response-cookie mutations required by the
 * inbound adapter.
 */
public record AuthSession(LoginResponse response, List<CookieMutation> cookies) {

    public AuthSession {
        cookies = cookies == null ? List.of() : List.copyOf(cookies);
    }
}
