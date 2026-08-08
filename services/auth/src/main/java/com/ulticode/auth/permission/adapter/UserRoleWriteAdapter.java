package com.ulticode.auth.permission.adapter;

import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.permission.mapper.UserRoleMapper;
import com.ulticode.auth.permission.port.UserRoleWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Default {@link UserRoleWritePort} implementation backed by
 * {@link UserRoleMapper}. The mapper is the only sanctioned writer
 * to the {@code users.role} column; this adapter just translates
 * "0 rows updated" into a typed not-found exception so callers do
 * not have to inspect the JDBC return value.
 */
@Component
@RequiredArgsConstructor
public class UserRoleWriteAdapter implements UserRoleWritePort {

    private final UserRoleMapper userRoleMapper;

    @Override
    public String changeRole(String userId, String newRole) {
        if (userId == null || userId.isBlank()) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND);
        }
        final int affected = userRoleMapper.updateRole(userId, newRole);
        // The role-administration service has already verified existence via
        // existsById, so a 0-row update here means the row's role was
        // already equal to the new value (the UPDATE's role <> newRole
        // guard made it a no-op). That is the idempotent path.
        if (affected == 0 && userRoleMapper.existsById(userId) == null) {
            throw new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND);
        }
        return newRole;
    }
}
