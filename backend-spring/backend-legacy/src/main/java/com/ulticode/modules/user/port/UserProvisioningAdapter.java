package com.ulticode.modules.user.port;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.modules.admin.port.UserProvisioningPort;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import com.ulticode.modules.auth.account.AuthAccountPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Production adapter for the admin module's {@link UserProvisioningPort}.
 *
 * <p>Absorbs the create-and-restore administrator invariant previously held by
 * the (now-deleted) package-private {@code AdministratorProvisioner}: ids come
 * from {@link UuidGenerator#newId()}, passwords are encoded here, active
 * accounts are pinned to active + unbanned, {@code joinedAt} is stamped via
 * {@link LocalDateTime#now(Clock)}, and persistence is owned in one place. The
 * interface lives in {@code admin.port} (consumer-owned); this adapter lives in
 * the user module (the provider) and is the sole touchpoint of
 * {@code UserMapper} for provisioning.
 */
@Component
@RequiredArgsConstructor
public class UserProvisioningAdapter implements UserProvisioningPort {

    private final UserMapper userMapper;
    private final AuthAccountPort accountPort;
    private final PasswordEncoder passwordEncoder;
    private final UuidGenerator uuidGenerator;
    private final Clock clock;

    @Override
    public long countActiveAdministrators() {
        Long count = userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .in(User::getRole, "ADMIN", "SUPER_ADMIN")
                .eq(User::getIsActive, true)
                .eq(User::getIsBanned, false));
        return count == null ? 0L : count;
    }

    @Override
    public boolean identityExists(String username, String email) {
        Long count = userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .and(w -> w.eq(User::getUsername, username).or().eq(User::getEmail, email)));
        return count != null && count > 0;
    }

    @Override
    public boolean emailConflicts(String email, String excludeId) {
        Long count = userMapper.selectCount(
            new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .ne(excludeId != null, User::getId, excludeId));
        return count != null && count > 0;
    }

    @Override
    public Optional<String> findIdByUsername(String username) {
        User user = userMapper.selectOne(
            new LambdaQueryWrapper<User>().eq(User::getUsername, username).last("LIMIT 1"));
        return Optional.ofNullable(user).map(User::getId);
    }

    @Override
    public void createAdministrator(AdministratorSpec spec) {
        User user = new User();
        user.setId(uuidGenerator.newId());
        user.setUsername(spec.username());
        user.setIsDeleted(0);
        user.setJoinedAt(LocalDateTime.now(clock));
        applyAdministratorFields(user, spec);
        accountPort.create(user);
    }

    @Override
    public void restoreAdministrator(String id, AdministratorSpec spec) {
        User existing = userMapper.selectById(id);
        if (existing == null) {
            throw new IllegalStateException("Cannot restore nonexistent administrator: " + id);
        }
        applyAdministratorFields(existing, spec);
        accountPort.updateBanStatus(id, false, null);
        accountPort.updatePassword(id, passwordEncoder.encode(spec.rawPassword()));
        accountPort.updateAccountCredentials(id, spec.username(), spec.email(), spec.role());
    }

    private void applyAdministratorFields(User user, AdministratorSpec spec) {
        user.setName(spec.name());
        user.setEmail(spec.email());
        user.setPassword(passwordEncoder.encode(spec.rawPassword()));
        user.setRole(spec.role());
        user.setIsActive(true);
        user.setIsBanned(false);
    }
}
