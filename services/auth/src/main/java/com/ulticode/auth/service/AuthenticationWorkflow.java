package com.ulticode.auth.service;

import com.ulticode.auth.session.AuthSession;

/**
 * HTTP-neutral authentication workflow owned by backend-auth.
 *
 * <p>The inbound HTTP adapter owns request DTO mapping and applies the cookie
 * mutations carried by {@link AuthSession}; this interface never receives a
 * servlet request or response.</p>
 */
public interface AuthenticationWorkflow {

    AuthSession login(String username, String password);

    AuthSession login(String username, String password, boolean adminOnly);

    AuthSession register(String username, String email, String password);

    AuthSession refresh(String refreshToken);

    AuthSession switchAccount(String accountId, String refreshToken, boolean adminOnly);

    AuthSession logout(String refreshToken);
}
