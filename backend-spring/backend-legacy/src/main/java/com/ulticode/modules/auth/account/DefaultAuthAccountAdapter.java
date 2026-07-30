package com.ulticode.modules.auth.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.auth.account.AuthAccountPort.PasswordResetRecord;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Default {@link AuthAccountPort} implementation (P3-OWNER-002).
 * Concentrates user account/credential/ban state mutations on the auth side.
 */
@Component
@RequiredArgsConstructor
public class DefaultAuthAccountAdapter implements AuthAccountPort {

    private final UserMapper userMapper;
    private final Clock clock;

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username)));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)));
    }

    @Override
    public Optional<User> findById(String userId) {
        return Optional.ofNullable(userMapper.selectById(userId));
    }

    @Override
    public User create(User user) {
        userMapper.insert(user);
        return user;
    }

    @Override
    public void updateLastLoginAt(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        User user = new User();
        user.setId(userId);
        user.setLastLoginAt(LocalDateTime.now(clock));
        userMapper.updateById(user);
    }

    @Override
    public void updatePassword(String userId, String hashedPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        user.setPassword(hashedPassword);
        userMapper.updateById(user);
    }

    @Override
    public void updateBanStatus(String userId, boolean isBanned, String bannedReason) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        user.setIsBanned(isBanned);
        user.setBannedReason(bannedReason == null ? "" : bannedReason);
        userMapper.updateById(user);
    }

    @Override
    public void updateActiveStatus(String userId, boolean isActive) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        user.setIsActive(isActive);
        userMapper.updateById(user);
    }

    @Override
    public void deleteAccount(String userId) {
        userMapper.deleteById(userId);
    }

    @Override
    public void updateAccountCredentials(String userId, String username, String email, String role) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        if (username != null) {
            user.setUsername(username);
        }
        if (email != null) {
            user.setEmail(email);
        }
        if (role != null) {
            user.setRole(role);
        }
        userMapper.updateById(user);
    }

    @Override
    public Optional<PasswordResetRecord> findPasswordReset(String userId) {
        return Optional.ofNullable(userMapper.selectById(userId))
                .filter(u -> u.getPasswordResetTokenHash() != null
                        && u.getPasswordResetExpiresAt() != null)
                .map(u -> new PasswordResetRecord(
                        u.getId(),
                        u.getPasswordResetTokenHash(),
                        u.getPasswordResetExpiresAt()
                                .atZone(java.time.ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli()));
    }

    @Override
    public void savePasswordReset(String userId, String hashedToken, long expiresAtEpochMs) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        user.setPasswordResetTokenHash(hashedToken);
        user.setPasswordResetExpiresAt(
                java.time.Instant.ofEpochMilli(expiresAtEpochMs)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime());
        userMapper.updateById(user);
    }

    @Override
    public void clearPasswordReset(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        userMapper.updateById(user);
    }

    @Override
    public Optional<User> findByOAuthEmail(String email) {
        return findByEmail(email);
    }

    @Override
    public List<User> findUsersWithActivePasswordReset(long nowEpochMs) {
        LocalDateTime now = java.time.Instant.ofEpochMilli(nowEpochMs)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .isNotNull(User::getPasswordResetTokenHash)
                        .gt(User::getPasswordResetExpiresAt, now));
    }
}
