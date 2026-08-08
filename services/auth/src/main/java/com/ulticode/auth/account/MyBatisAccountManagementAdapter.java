package com.ulticode.auth.account;

import com.ulticode.auth.account.entity.AuthAccountEntity;
import com.ulticode.auth.account.mapper.AccountManagementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/** Production MySQL-backed implementation of {@link AccountManagementPort}. */
@Component
@ConditionalOnProperty(name = "app.auth.account-store", havingValue = "mysql", matchIfMissing = true)
@RequiredArgsConstructor
public class MyBatisAccountManagementAdapter implements AccountManagementPort {

    private final AccountManagementMapper mapper;

    @Override
    public Optional<AuthAccountRecord> findById(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findById(accountId.trim())).map(this::toRecord);
    }

    @Override
    public Optional<AuthAccountRecord> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findByUsername(username.trim())).map(this::toRecord);
    }

    @Override
    public Optional<AuthAccountRecord> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findByEmail(email.trim())).map(this::toRecord);
    }

    @Override
    public AuthAccountRecord create(AuthAccountRecord account) {
        AuthAccountEntity entity = toEntity(account);
        mapper.insert(entity);
        return toRecord(entity);
    }

    @Override
    public boolean updateCredentials(String accountId, String username, String email,
                                     String updatedBy) {
        if (accountId == null || accountId.isBlank()
                || username == null || username.isBlank()
                || email == null || email.isBlank()) {
            return false;
        }
        return mapper.updateCredentials(accountId.trim(), username.trim(), email.trim(), updatedBy) > 0;
    }

    @Override
    public boolean updatePassword(String accountId, String hashedPassword, String updatedBy) {
        if (accountId == null || accountId.isBlank()
                || hashedPassword == null || hashedPassword.isBlank()) {
            return false;
        }
        return mapper.updatePassword(accountId.trim(), hashedPassword, updatedBy) > 0;
    }

    @Override
    public boolean softDelete(String accountId, String deletedBy) {
        if (accountId == null || accountId.isBlank()) {
            return false;
        }
        return mapper.softDelete(accountId.trim(), deletedBy) > 0;
    }

    private AuthAccountRecord toRecord(AuthAccountEntity entity) {
        return new AuthAccountRecord(
                entity.getId(),
                entity.getUsername(),
                entity.getEmail(),
                entity.getPassword(),
                entity.getRole(),
                entity.getActive(),
                entity.getBanned(),
                entity.getBannedUntil(),
                entity.getJoinedAt(),
                entity.getAuthzVersion() != null ? entity.getAuthzVersion() : 0L);
    }

    private AuthAccountEntity toEntity(AuthAccountRecord record) {
        AuthAccountEntity entity = new AuthAccountEntity();
        entity.setId(record.id());
        entity.setUsername(record.username());
        entity.setEmail(record.email());
        entity.setPassword(record.password());
        entity.setRole(record.role());
        entity.setActive(record.isActive());
        entity.setBanned(record.isBanned());
        entity.setBannedUntil(record.bannedUntil());
        entity.setJoinedAt(record.joinedAt() != null ? record.joinedAt() : LocalDateTime.now());
        entity.setAuthzVersion(record.authzVersion());
        return entity;
    }
}
