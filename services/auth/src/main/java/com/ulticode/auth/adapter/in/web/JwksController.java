package com.ulticode.auth.adapter.in.web;

import com.ulticode.auth.security.jwt.RsaKeyManager;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AUTH-COMP-006: JWKS endpoint exposing the Auth service's public key(s).
 *
 * <p>Returns the JWK Set (RFC 7517) at {@code GET /auth/jwks} so resource
 * servers (backend-app, backend-admin) can verify RS256-signed access tokens
 * without sharing the symmetric secret.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class JwksController {

    private final RsaKeyManager rsaKeyManager;

    @GetMapping("/jwks")
    public Map<String, Object> getJwks() {
        return rsaKeyManager.toJwkSet();
    }
}
