package com.ulticode.auth.service;

import com.ulticode.auth.account.AuthAccountQueryPort;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.error.AuthBusinessException;
import com.ulticode.auth.error.AuthErrorCode;
import com.ulticode.auth.permission.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Default backend-auth implementation of {@link CurrentSessionQuery}.
 *
 * <p>The query port exposes {@link AuthAccountDTO}, which deliberately omits
 * password-bearing fields. HTTP response shaping remains in
 * {@code AuthController}.</p>
 */
@Service
@RequiredArgsConstructor
public class DefaultCurrentSessionQuery implements CurrentSessionQuery {

    private final AuthAccountQueryPort accountQueryPort;
    private final PermissionService permissionService;

    @Override
    public CurrentUser currentUser(String accountId) {
        AuthAccountDTO account = accountQueryPort.findById(accountId)
                .orElseThrow(() -> new AuthBusinessException(AuthErrorCode.AUTH_USER_NOT_FOUND));

        return new CurrentUser(
                account.accountId(),
                account.username(),
                account.email(),
                account.role(),
                account.active(),
                account.banned(),
                account.joinedAt()
        );
    }

    @Override
    public List<String> permissions(String accountId) {
        return permissionService.getUserPermissionStrings(accountId);
    }
}
