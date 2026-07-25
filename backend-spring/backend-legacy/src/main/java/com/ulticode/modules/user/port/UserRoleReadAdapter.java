package com.ulticode.modules.user.port;

import com.ulticode.modules.permission.port.UserRoleReadPort;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Production adapter for the permission module's {@link UserRoleReadPort}.
 *
 * <p>Lives in the user module (the provider) and is the sole touchpoint of the
 * user persistence layer for role lookups, keeping {@code UserMapper} inside
 * user. The interface itself lives in {@code permission.port} — a consumer-owned
 * seam, per {@code backend-spring/AGENTS.md} (cross-module dependencies use
 * consumer-owned ports).
 */
@Component
@RequiredArgsConstructor
public class UserRoleReadAdapter implements UserRoleReadPort {

    private final UserMapper userMapper;

    @Override
    public Optional<UserRole> findRole(String userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.selectById(userId))
                .map(u -> new UserRole(u.getRole()));
    }
}
