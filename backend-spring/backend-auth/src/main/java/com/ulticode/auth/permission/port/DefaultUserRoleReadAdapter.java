package com.ulticode.auth.permission.port;

import com.ulticode.auth.account.AuthAccountPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default implementation of {@link UserRoleReadPort} for backend-auth.
 *
 * <p>Resolves the user's role via {@link AuthAccountPort#findById}, which
 * reads the authoritative {@code users.role} column. The previous stub
 * always returned {@code role = null}, causing
 * {@code PermissionServiceImpl.getUserPermissionStrings} to skip the
 * entire {@code role_permissions} query — so every API consumer saw an
 * empty permission list despite the table holding 469 grants.
 */
@Component
public class DefaultUserRoleReadAdapter implements UserRoleReadPort {

    private final AuthAccountPort authAccountPort;

    public DefaultUserRoleReadAdapter(AuthAccountPort authAccountPort) {
        this.authAccountPort = authAccountPort;
    }

    @Override
    public Optional<UserRole> findRole(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return authAccountPort.findById(userId)
                .map(record -> new UserRole(record.role()));
    }
}
