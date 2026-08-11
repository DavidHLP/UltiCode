package com.ulticode.auth.account;

import com.ulticode.auth.account.entity.AuthAccountEntity;
import com.ulticode.auth.account.mapper.AuthAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Production MySQL-backed implementation of {@link AuthAccountPort}. */
@Component
@ConditionalOnProperty(name = "app.auth.account-store", havingValue = "mysql", matchIfMissing = true)
@RequiredArgsConstructor
public class MyBatisAuthAccountAdapter implements AuthAccountPort {

    private final AuthAccountMapper mapper;

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
    public Optional<AuthAccountRecord> findById(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.findById(userId.trim())).map(this::toRecord);
    }

    @Override
    public List<AuthAccountRecord> findByIds(Set<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        Set<String> clean = userIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
        if (clean.isEmpty()) {
            return List.of();
        }
        return mapper.findByIds(clean).stream()
                .filter(Objects::nonNull)
                .map(this::toRecord)
                .toList();
    }

    @Override
    public List<String> findActiveAccountIds() {
        return mapper.findActiveAccountIds();
    }

    @Override
    public AuthAccountRecord create(AuthAccountRecord record) {
        AuthAccountEntity entity = toEntity(record);
        mapper.insert(entity);
        return toRecord(entity);
    }

    @Override
    public void updateLastLoginAt(String userId) {
        if (userId != null && !userId.isBlank()) {
            mapper.updateLastLoginAt(userId.trim());
        }
    }

    @Override
    public void updatePassword(String userId, String hashedPassword) {
        if (userId != null && !userId.isBlank()) {
            mapper.updatePassword(userId.trim(), hashedPassword);
        }
    }

    @Override
    public boolean updateAccountIfVersion(String userId, boolean active, boolean banned, String role, long expectedVersion) {
        if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
            return false;
        }
        return mapper.updateAccountIfVersion(userId.trim(), active, banned, role.trim().toUpperCase(), expectedVersion) > 0;
    }

    @Override
    public Optional<PasswordResetRecord> findPasswordReset(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        AuthAccountEntity entity = mapper.findById(userId.trim());
        if (entity == null || entity.getPasswordResetTokenHash() == null
                || entity.getPasswordResetExpiresAt() == null) {
            return Optional.empty();
        }
        long epochMs = entity.getPasswordResetExpiresAt()
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        return Optional.of(new PasswordResetRecord(entity.getId(), entity.getPasswordResetTokenHash(), epochMs));
    }

    @Override
    public void savePasswordReset(String userId, String hashedToken, long expiresAtEpochMs) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        LocalDateTime expiresAt = Instant.ofEpochMilli(expiresAtEpochMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        mapper.savePasswordReset(userId.trim(), hashedToken, expiresAt);
    }

    @Override
    public void clearPasswordReset(String userId) {
        if (userId != null && !userId.isBlank()) {
            mapper.clearPasswordReset(userId.trim());
        }
    }

    @Override
    public List<AuthAccountRecord> findUsersWithActivePasswordReset(long nowEpochMs) {
        LocalDateTime now = Instant.ofEpochMilli(nowEpochMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
        return mapper.findUsersWithActivePasswordReset(now).stream()
                .filter(Objects::nonNull)
                .map(this::toRecord)
                .toList();
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
                entity.getAuthzVersion() != null ? entity.getAuthzVersion() : 0L
        );
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
