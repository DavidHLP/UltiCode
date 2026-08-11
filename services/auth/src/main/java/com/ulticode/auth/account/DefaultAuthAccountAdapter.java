package com.ulticode.auth.account;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fallback adapter for {@link AuthAccountPort} inside backend-auth shell.
 * Active only when {@code app.auth.account-store=memory}.
 */
@Component
@ConditionalOnProperty(name = "app.auth.account-store", havingValue = "memory")
public class DefaultAuthAccountAdapter implements AuthAccountPort {

    private final Map<String, AuthAccountRecord> accountsById = new ConcurrentHashMap<>();
    private final Map<String, AuthAccountRecord> accountsByUsername = new ConcurrentHashMap<>();
    private final Map<String, AuthAccountRecord> accountsByEmail = new ConcurrentHashMap<>();
    private final Map<String, PasswordResetRecord> resetsByUserId = new ConcurrentHashMap<>();

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
    public List<AuthAccountRecord> findByIds(Set<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        return userIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .map(accountsById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public List<String> findActiveAccountIds() {
        return accountsById.values().stream()
                .filter(account -> Boolean.TRUE.equals(account.isActive()))
                .filter(account -> !Boolean.TRUE.equals(account.isBanned()))
                .map(AuthAccountRecord::id)
                .toList();
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

    @Override
    public void updatePassword(String userId, String hashedPassword) {
        AuthAccountRecord existing = accountsById.get(userId);
        if (existing != null) {
            AuthAccountRecord updated = new AuthAccountRecord(
                    existing.id(), existing.username(), existing.email(), hashedPassword,
                    existing.role(), existing.isActive(), existing.isBanned(),
                    existing.bannedUntil(), existing.joinedAt(), existing.authzVersion()
            );
            replace(existing, updated);
        }
    }

    @Override
    public synchronized boolean updateAccountIfVersion(String userId, boolean active, boolean banned,
                                                        String role, long expectedVersion) {
        AuthAccountRecord existing = accountsById.get(userId);
        if (existing == null || existing.authzVersion() != expectedVersion) {
            return false;
        }
        AuthAccountRecord updated = new AuthAccountRecord(
                existing.id(), existing.username(), existing.email(), existing.password(),
                role, active, banned, existing.bannedUntil(), existing.joinedAt(), expectedVersion + 1
        );
        replace(existing, updated);
        return true;
    }

    private void replace(AuthAccountRecord existing, AuthAccountRecord updated) {
        accountsById.put(existing.id(), updated);
        accountsByUsername.put(existing.username(), updated);
        if (existing.email() != null && !existing.email().isBlank()) {
            accountsByEmail.put(existing.email(), updated);
        }
    }

    @Override
    public Optional<PasswordResetRecord> findPasswordReset(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(resetsByUserId.get(userId.trim()));
    }

    @Override
    public void savePasswordReset(String userId, String hashedToken, long expiresAtEpochMs) {
        resetsByUserId.put(userId, new PasswordResetRecord(userId, hashedToken, expiresAtEpochMs));
    }

    @Override
    public void clearPasswordReset(String userId) {
        resetsByUserId.remove(userId);
    }

    @Override
    public List<AuthAccountRecord> findUsersWithActivePasswordReset(long nowEpochMs) {
        List<AuthAccountRecord> result = new ArrayList<>();
        for (PasswordResetRecord record : resetsByUserId.values()) {
            if (record.expiresAtEpochMs() > nowEpochMs) {
                AuthAccountRecord acc = accountsById.get(record.userId());
                if (acc != null) {
                    result.add(acc);
                }
            }
        }
        return result;
    }
}
