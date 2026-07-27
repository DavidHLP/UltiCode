package com.ulticode.auth.account;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fallback adapter for {@link AuthAccountPort} inside backend-auth shell.
 */
@Component
public class DefaultAuthAccountAdapter implements AuthAccountPort {

    private final Map<String, AuthAccountRecord> accountsById = new ConcurrentHashMap<>();
    private final Map<String, AuthAccountRecord> accountsByUsername = new ConcurrentHashMap<>();
    private final Map<String, AuthAccountRecord> accountsByEmail = new ConcurrentHashMap<>();

    @Override
    public Optional<AuthAccountRecord> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(accountsByUsername.get(username.trim()));
    }

    @Override
    public Optional<AuthAccountRecord> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(accountsByEmail.get(email.trim()));
    }

    @Override
    public Optional<AuthAccountRecord> findById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(accountsById.get(userId.trim()));
    }

    @Override
    public AuthAccountRecord create(AuthAccountRecord record) {
        accountsById.put(record.id(), record);
        accountsByUsername.put(record.username(), record);
        if (record.email() != null && !record.email().isBlank()) {
            accountsByEmail.put(record.email(), record);
        }
        return record;
    }

    @Override
    public void updateLastLoginAt(String userId) {
        // No-op for fallback adapter
    }
}
