package com.ulticode.websecurity.jwt;

/** Verifies an access token and returns only trusted authentication claims. */
@FunctionalInterface
public interface AccessTokenVerifier {

    AccessTokenClaims verify(String token);
}
