package com.ulticode.modules.auth.account;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.auth.account.AuthAccountPort.PasswordResetRecord;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Default {@link AuthAccountPort} implementation. Concentrates every
 * user-table / OAuth-identity read and write that the auth services used
 * to do directly against {@code UserMapper} /
 * {@code UserOAuthIdentityMapper}.
 *
 * <p>Architecture-review candidate #5 — the previous code had
 * {@code AuthServiceImpl}, {@code OAuthService}, and
 * {@code PasswordResetService} all building {@code LambdaQueryWrapper}
 * against {@code User} and reading token columns directly. This adapter
 * is the only place that touches the user module's mappers on the
 * auth side.
 *
 * @author ulticode
 */
@Component
@RequiredArgsConstructor
public class DefaultAuthAccountAdapter implements AuthAccountPort {

    private final UserMapper userMapper;

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
    public void updatePassword(String userId, String hashedPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        user.setPassword(hashedPassword);
        userMapper.updateById(user);
    }

    @Override
    public Optional<PasswordResetRecord> findPasswordReset(String userId) {
        // The reset token lives on the user row (User.passwordResetTokenHash +
        // .passwordResetExpiresAt). We expose the (userId, token, expiresAt)
        // triple to the auth service so it does not need to know the column
        // layout.
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
    public void updateAvatar(String userId, String avatarUrl) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return;
        }
        user.setAvatar(avatarUrl);
        userMapper.updateById(user);
    }

    /**
     * Find any user whose reset token is still active (non-null hash and
     * non-expired). The auth service then BCrypt-matches the supplied
     * token. This used to be an inline {@code LambdaQueryWrapper.isNotNull(...).gt(...)}
     * in {@code PasswordResetService}.
     */
    @Override
    public List<User> findUsersWithActivePasswordReset(long nowEpochMs) {
        java.time.LocalDateTime now = java.time.Instant.ofEpochMilli(nowEpochMs)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDateTime();
        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .isNotNull(User::getPasswordResetTokenHash)
                        .gt(User::getPasswordResetExpiresAt, now));
    }
}
