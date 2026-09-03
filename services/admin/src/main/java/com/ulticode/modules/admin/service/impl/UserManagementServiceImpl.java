package com.ulticode.modules.admin.service.impl;

import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.auth.api.command.CreateAccountCommand;
import com.ulticode.auth.api.command.DeleteAccountCommand;
import com.ulticode.auth.api.command.ResetPasswordCommand;
import com.ulticode.auth.api.command.UpdateAccountCredentialsCommand;
import com.ulticode.auth.api.dto.AccountMutationDTO;
import com.ulticode.auth.api.dto.AccountStateDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.service.AccountAdministrationService;
import com.ulticode.auth.api.service.AccountManagementService;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.auth.AdminActors;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.modules.admin.dto.AdminCreateUserDTO;
import com.ulticode.modules.admin.dto.AdminUpdateUserDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.query.AdminUserDetailQuery;
import com.ulticode.modules.admin.query.AdminUserDetailResult;
import com.ulticode.modules.admin.service.UserManagementService;
import com.ulticode.admin.port.UserProfilePort;
import com.ulticode.app.api.command.UpdateProfileCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.ulticode.common.rpc.RpcPolicy;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private AccountManagementService accountManagementService;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AccountQueryService accountQueryService;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.WRITE_TIMEOUT_MS, retries = RpcPolicy.WRITE_RETRIES, check = false)
    private AccountAdministrationService accountAdministrationService;

    private final UserProfilePort userProfilePort;
    private final AuditRecorder auditRecorder;
    private final AdminUserDetailQuery adminUserDetailQuery;
    private final CurrentUserProvider currentUserProvider;

    @Override
    @Audited(action = AuditVocabulary.CREATE_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "#result.id")
    public AdminUserVO createUser(AdminCreateUserDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getPassword())) {
            throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, "Password is required");
        }
        checkQueryServiceAvailable();
        checkManagementServiceAvailable();

        requireIdentityAvailable(
                accountQueryService.getAccountByUsername(dto.getUsername()),
                "username availability check", "Username already exists");
        if (StringUtils.hasText(dto.getEmail())) {
            requireIdentityAvailable(
                    accountQueryService.getAccountByEmail(dto.getEmail()),
                    "email availability check", "Email already exists");
        }

        String role = StringUtils.hasText(dto.getRole()) ? dto.getRole() : "USER";
        String commandId = UUID.randomUUID().toString();
        CreateAccountCommand createCmd = new CreateAccountCommand(
                commandId,
                IdMetadata.mint(),
                authActor("admin create user"),
                currentTrace(),
                dto.getUsername(),
                dto.getEmail(),
                dto.getPassword(),
                role
        );

        RpcResult<AccountMutationDTO> createResult = accountManagementService.createAccount(createCmd);
        if (createResult == null || !createResult.success() || createResult.data() == null) {
            throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, "Failed to create account on Auth provider");
        }

        String newUserId = createResult.data().accountId();

        if (StringUtils.hasText(dto.getName())) {
            UpdateProfileCommand profileCmd = new UpdateProfileCommand(
                    UUID.randomUUID().toString(),
                    IdMetadata.mint(),
                    appActor("admin create user"),
                    currentTrace(),
                    newUserId,
                    dto.getName(), null, null, null, null, null, null, null, null);
            userProfilePort.updateProfile(profileCmd);
        }

        log.info("User created: {} by admin", newUserId);
        return userFromDetail(newUserId);
    }

    @Override
    @Audited(action = AuditVocabulary.UPDATE_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public AdminUserVO updateUser(String id, AdminUpdateUserDTO dto) {
        checkQueryServiceAvailable();
        checkManagementServiceAvailable();

        RpcResult<AuthAccountDTO> currentRpc = accountQueryService.getAccountById(id);
        if (currentRpc == null || !currentRpc.success() || currentRpc.data() == null) {
            throw new BusinessException(AdminErrorCode.USER_NOT_FOUND);
        }

        AuthAccountDTO current = currentRpc.data();

        if (StringUtils.hasText(dto.getUsername()) && !dto.getUsername().equals(current.username())) {
            requireIdentityAvailable(
                    accountQueryService.getAccountByUsername(dto.getUsername()),
                    "username availability check", "Username already exists");
        }

        if (StringUtils.hasText(dto.getEmail()) && !dto.getEmail().equals(current.email())) {
            requireIdentityAvailable(
                    accountQueryService.getAccountByEmail(dto.getEmail()),
                    "email availability check", "Email already exists");
        }

        AuditContext.setOldValues(Map.of(
                "username", current.username(),
                "email", current.email(),
                "role", current.role(),
                "isActive", current.active()
        ));

        String newUsername = StringUtils.hasText(dto.getUsername()) ? dto.getUsername() : current.username();
        String newEmail = StringUtils.hasText(dto.getEmail()) ? dto.getEmail() : current.email();
        UpdateAccountCredentialsCommand updateCredsCmd = new UpdateAccountCredentialsCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                authActor("admin update credentials"),
                currentTrace(),
                id,
                newUsername,
                newEmail
        );
        requireSuccessful(accountManagementService.updateCredentials(updateCredsCmd),
                "Account credentials update failed");

        UpdateProfileCommand profileCmd = new UpdateProfileCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                appActor("admin update user"),
                currentTrace(),
                id,
                dto.getName(),
                dto.getAvatar(),
                dto.getBio(),
                dto.getCompany(),
                dto.getGithub(),
                dto.getLocation(),
                dto.getTwitter(),
                dto.getWebsite(),
                dto.getPreferredLanguage());
        userProfilePort.updateProfile(profileCmd);

        if (StringUtils.hasText(dto.getRole()) && !dto.getRole().equals(current.role())) {
            if (accountAdministrationService == null) {
                throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                        "AccountAdministrationService unavailable");
            }
            String stableKey = "auth-role-update-" + currentTrace().traceId() + "-" + id;
            String commandId = UUID.nameUUIDFromBytes(stableKey.getBytes()).toString();
            ChangeAuthorizationCommand command = new ChangeAuthorizationCommand(
                    commandId, IdMetadata.of(stableKey, null), authActor("admin user update"), currentTrace(),
                    id, current.authzVersion(), dto.getRole(), Collections.emptySet(), "update user role"
            );
            requireSuccessful(accountAdministrationService.changeAuthorization(command),
                    "Account role update failed");
        }

        AuditContext.setNewValues(Map.of(
                "username", newUsername,
                "email", newEmail,
                "role", dto.getRole() != null ? dto.getRole() : current.role(),
                "isActive", dto.getIsActive() != null ? dto.getIsActive() : current.active()
        ));

        log.info("User updated: {}", id);
        return userFromDetail(id);
    }

    @Override
    @Audited(action = AuditVocabulary.BAN_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public AdminUserVO banUser(String id, String reason, String until) {
        checkQueryServiceAvailable();
        AuthAccountDTO current = getAccountOrThrow(id);

        ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                authActor("admin ban user"),
                currentTrace(),
                id,
                current.authzVersion(),
                ChangeAccountStateCommand.AccountStateAction.BAN,
                reason
        );
        executeStateChange(command);
        log.info("User banned: {} - reason: {}", id, reason);
        return userFromDetail(id);
    }

    @Override
    @Audited(action = AuditVocabulary.UNBAN_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public AdminUserVO unbanUser(String id) {
        checkQueryServiceAvailable();
        AuthAccountDTO current = getAccountOrThrow(id);

        ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                authActor("admin unban user"),
                currentTrace(),
                id,
                current.authzVersion(),
                ChangeAccountStateCommand.AccountStateAction.UNBAN,
                "admin unban user"
        );
        executeStateChange(command);
        log.info("User unbanned: {}", id);
        return userFromDetail(id);
    }

    @Override
    @Audited(action = AuditVocabulary.DELETE_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public void deleteUser(String id) {
        checkManagementServiceAvailable();
        DeleteAccountCommand command = new DeleteAccountCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                authActor("admin delete user"),
                currentTrace(),
                id,
                "admin delete user"
        );
        requireSuccessful(accountManagementService.deleteAccount(command), "Account deletion failed");
        log.info("User deleted: {}", id);
    }

    @Override
    @Audited(action = AuditVocabulary.RESET_PASSWORD, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public void resetPassword(String id, String newPassword) {
        checkManagementServiceAvailable();
        AuditContext.setOldValues(Map.of("passwordChanged", false));
        AuditContext.setNewValues(Map.of("passwordChanged", true));

        ResetPasswordCommand command = new ResetPasswordCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                authActor("admin reset password"),
                currentTrace(),
                id,
                newPassword,
                "admin reset password"
        );
        requireSuccessful(accountManagementService.resetPassword(command), "Password reset failed");
        log.info("Password reset for user: {}", id);
    }

    @Override
    public List<BanResult> bulkBan(List<String> ids, String reason) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<BanResult> results = new ArrayList<>();
        for (String id : ids) {
            try {
                banUser(id, reason, null);
                results.add(new BanResult(id, true, null));
            } catch (Exception e) {
                results.add(new BanResult(id, false, e.getMessage()));
            }
        }
        return results;
    }

    @Override
    public List<BanResult> bulkUnban(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<BanResult> results = new ArrayList<>();
        for (String id : ids) {
            try {
                unbanUser(id);
                results.add(new BanResult(id, true, null));
            } catch (Exception e) {
                results.add(new BanResult(id, false, e.getMessage()));
            }
        }
        return results;
    }

    @Override
    public List<DeleteResult> bulkDelete(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeleteResult> results = new ArrayList<>();
        for (String id : ids) {
            try {
                deleteUser(id);
                results.add(new DeleteResult(id, true, null));
            } catch (Exception e) {
                results.add(new DeleteResult(id, false, e.getMessage()));
            }
        }
        return results;
    }

    private void executeStateChange(ChangeAccountStateCommand command) {
        if (accountAdministrationService == null) {
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                    "AccountAdministrationService unavailable");
        }
        requireSuccessful(accountAdministrationService.changeState(command),
                "Account state mutation failed");
    }

    private static void requireSuccessful(RpcResult<?> result, String message) {
        if (result == null || !result.success()) {
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR, message);
        }
    }

    /**
     * Fail closed identity conflict check: an existing account rejects the
     * mutation, an explicit ACCOUNT_NOT_FOUND passes, and any other answer
     * (null result, RPC error) aborts instead of being treated as "free".
     */
    private static void requireIdentityAvailable(
            RpcResult<AuthAccountDTO> result, String operation, String conflictMessage) {
        if (result != null && result.success() && result.data() != null) {
            throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, conflictMessage);
        }
        if (result != null && result.success()) {
            return;
        }
        if (result != null && result.error() != null
                && result.error().code() == com.ulticode.auth.api.error.AuthErrorCode.ACCOUNT_NOT_FOUND.code()) {
            return;
        }
        throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR,
                "AccountQueryService unavailable during " + operation);
    }

    private AuthAccountDTO getAccountOrThrow(String id) {
        RpcResult<AuthAccountDTO> currentRpc = accountQueryService.getAccountById(id);
        if (currentRpc == null || !currentRpc.success() || currentRpc.data() == null) {
            throw new BusinessException(AdminErrorCode.USER_NOT_FOUND);
        }
        return currentRpc.data();
    }

    private void checkQueryServiceAvailable() {
        if (accountQueryService == null) {
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR, "AccountQueryService unavailable");
        }
    }

    private void checkManagementServiceAvailable() {
        if (accountManagementService == null) {
            throw new BusinessException(AdminErrorCode.UNKNOWN_ERROR, "AccountManagementService unavailable");
        }
    }

    private com.ulticode.auth.api.command.ActorDelegation authActor(String rationale) {
        String actorId = currentActorId();
        return new com.ulticode.auth.api.command.ActorDelegation(
                currentActorType(), actorId, actorId, rationale);
    }

    private com.ulticode.common.command.ActorDelegation appActor(String rationale) {
        String actorId = currentActorId();
        return new com.ulticode.common.command.ActorDelegation(
                currentActorType(), actorId, actorId, rationale);
    }

    private String currentActorType() {
        return AdminActors.typeOf(currentUserProvider);
    }

    private String currentActorId() {
        String actorId = currentUserProvider.getCurrentUserId();
        if (actorId == null || actorId.isBlank()) {
            throw new BusinessException(AdminErrorCode.UNAUTHORIZED, "Authenticated admin actor is required");
        }
        return actorId;
    }

    private TraceMetadata currentTrace() {
        String reqId = TraceIdUtil.current();
        if (reqId == null || reqId.isBlank()) {
            reqId = "t-" + UUID.randomUUID();
        }
        return new TraceMetadata(reqId, null, null, null);
    }
    private AdminUserVO userFromDetail(String id) {
        AdminUserDetailResult result = adminUserDetailQuery.loadUserDetail(id);
        if (result == null || result.failure() == AdminUserDetailResult.Failure.NOT_FOUND) {
            throw new BusinessException(AdminErrorCode.USER_NOT_FOUND);
        }
        if (result.failure() == AdminUserDetailResult.Failure.TRANSPORT_UNAVAILABLE
                || result.user() == null) {
            throw new BusinessException(
                    AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                    "Admin user detail query unavailable");
        }
        return result.user();
    }
}
