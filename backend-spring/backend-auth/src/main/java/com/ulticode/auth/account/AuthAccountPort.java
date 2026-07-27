package com.ulticode.auth.account;

import java.util.Optional;

/**
 * Authentication-side account persistence seam.
 */
public interface AuthAccountPort {

    Optional<AuthAccountRecord> findByUsername(String username);

    Optional<AuthAccountRecord> findByEmail(String email);

    Optional<AuthAccountRecord> findById(String userId);

    AuthAccountRecord create(AuthAccountRecord record);

    void updateLastLoginAt(String userId);
}
