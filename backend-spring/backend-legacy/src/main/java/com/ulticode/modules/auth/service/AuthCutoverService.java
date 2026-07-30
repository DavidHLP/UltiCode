package com.ulticode.modules.auth.service;

import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthorizationSnapshotDTO;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.auth.api.service.AuthorizationSnapshotService;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.client.BackendAuthRoleAdminClient;
import com.ulticode.modules.auth.account.DefaultAuthAccountAdapter;
import com.ulticode.modules.permission.service.PermissionService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * P7-AUTH-CONSUMER-CUTOVER-001: Feature-flagged consumer routing adapter for auth operations.
 *
 * <p>When {@code app.features.auth-dubbo-cutover=false} (default), delegates directly to
 * local legacy services/mappers/adapters. When {@code true}, routes identity, snapshot, and administrative
 * mutations through Dubbo RPC providers in {@code backend-auth}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthCutoverService {

    private final UserMapper userMapper;
    private final DefaultAuthAccountAdapter defaultAuthAccountAdapter;
    private final PermissionService permissionService;
    private final BackendAuthRoleAdminClient backendAuthRoleAdminClient;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private IdentityQueryService identityQueryService;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private AuthorizationSnapshotService authorizationSnapshotService;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 0, check = false)
    private AccountAdministrationService accountAdministrationService;

    @Value("${app.features.auth-dubbo-cutover:false}")
    private boolean dubboEnabled;

    /**
     * Read identity projection by account ID.
     */
    public UserIdentityDTO getIdentity(String accountId) {
        if (!dubboEnabled) {
            User user = userMapper.selectById(accountId);
            if (user == null || (user.getIsDeleted() != null && user.getIsDeleted() == 1)) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            return new UserIdentityDTO(
                    user.getId(),
                    user.getUsername(),
                    user.getRole(),
                    Boolean.TRUE.equals(user.getIsActive()),
                    Boolean.TRUE.equals(user.getIsBanned())
            );
        }

        RpcResult<UserIdentityDTO> result = identityQueryService.getIdentity(accountId);
        if (!result.success()) {
            throw mapError(result);
        }
        return result.data();
    }

    /**
     * Read authorization snapshot by account ID.
     */
    public AuthorizationSnapshotDTO getSnapshot(String accountId) {
        if (!dubboEnabled) {
            User user = userMapper.selectById(accountId);
            if (user == null || (user.getIsDeleted() != null && user.getIsDeleted() == 1)) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            List<String> permStrings = permissionService != null ? permissionService.getUserPermissionStrings(user.getId()) : Collections.emptyList();
            Set<String> perms = (permStrings == null) ? Collections.emptySet() : new HashSet<>(permStrings);
            return new AuthorizationSnapshotDTO(
                    user.getId(),
                    user.getRole(),
                    perms,
                    0L
            );
        }

        RpcResult<AuthorizationSnapshotDTO> result = authorizationSnapshotService.getSnapshot(accountId);
        if (!result.success()) {
            throw mapError(result);
        }
        return result.data();
    }

    /**
     * Mutate account lifecycle state (active / banned / disabled).
     */
    public AccountStateDTO changeState(ChangeAccountStateCommand command) {
        if (!dubboEnabled) {
            User user = userMapper.selectById(command.accountId());
            if (user == null || (user.getIsDeleted() != null && user.getIsDeleted() == 1)) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            boolean targetActive = Boolean.TRUE.equals(user.getIsActive());
            boolean targetBanned = Boolean.TRUE.equals(user.getIsBanned());
            switch (command.action()) {
                case DISABLE -> targetActive = false;
                case ENABLE -> targetActive = true;
                case BAN -> targetBanned = true;
                case UNBAN -> targetBanned = false;
            }

            if (command.action() == ChangeAccountStateCommand.AccountStateAction.BAN || command.action() == ChangeAccountStateCommand.AccountStateAction.UNBAN) {
                defaultAuthAccountAdapter.updateBanStatus(command.accountId(), targetBanned, command.rationale());
            } else {
                defaultAuthAccountAdapter.updateActiveStatus(command.accountId(), targetActive);
            }

            return new AccountStateDTO(command.accountId(), targetActive, targetBanned, 0L);
        }

        RpcResult<AccountStateDTO> result = accountAdministrationService.changeState(command);
        if (!result.success()) {
            throw mapError(result);
        }
        return result.data();
    }

    /**
     * Mutate account authoritative role / permissions.
     */
    public AuthorizationSnapshotDTO changeAuthorization(ChangeAuthorizationCommand command) {
        if (!dubboEnabled) {
            User user = userMapper.selectById(command.accountId());
            if (user == null || (user.getIsDeleted() != null && user.getIsDeleted() == 1)) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            defaultAuthAccountAdapter.updateAccountCredentials(command.accountId(), null, null, command.role());

            if (command.permissions() != null && !command.permissions().isEmpty() && backendAuthRoleAdminClient != null) {
                for (String perm : command.permissions()) {
                    String[] parts = perm.split(":", 2);
                    if (parts.length == 2) {
                        backendAuthRoleAdminClient.grantPermission(command.accountId(), parts[0], parts[1], null);
                    }
                }
            }

            List<String> permStrings = permissionService != null ? permissionService.getUserPermissionStrings(command.accountId()) : Collections.emptyList();
            Set<String> perms = (permStrings == null) ? Collections.emptySet() : new HashSet<>(permStrings);
            return new AuthorizationSnapshotDTO(command.accountId(), command.role(), perms, 0L);
        }

        RpcResult<AuthorizationSnapshotDTO> result = accountAdministrationService.changeAuthorization(command);
        if (!result.success()) {
            throw mapError(result);
        }
        return result.data();
    }

    private static BusinessException mapError(RpcResult<?> result) {
        var err = result.error();
        if (err == null) {
            return new BusinessException(ErrorCode.UNKNOWN_ERROR, "RPC failed without error payload");
        }
        int code = err.code();
        if (code == 40401) {
            return new BusinessException(ErrorCode.USER_NOT_FOUND, err.message());
        }
        if (code == 40903) {
            return new BusinessException(ErrorCode.CONFLICT, err.message());
        }
        return new BusinessException(ErrorCode.UNKNOWN_ERROR, err.message());
    }
}
