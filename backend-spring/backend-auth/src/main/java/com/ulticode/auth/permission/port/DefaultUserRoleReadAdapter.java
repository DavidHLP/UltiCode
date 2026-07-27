package com.ulticode.auth.permission.port;

import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Default fallback implementation of {@link UserRoleReadPort} for backend-auth.
 */
@Component
public class DefaultUserRoleReadAdapter implements UserRoleReadPort {

    @Override
    public Optional<UserRole> findRole(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        // Fallback default role view for backend-auth
        return Optional.of(new UserRole(null));
    }
}
