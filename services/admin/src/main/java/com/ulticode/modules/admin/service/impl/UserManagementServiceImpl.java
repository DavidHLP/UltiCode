package com.ulticode.modules.admin.service.impl;

import com.ulticode.auth.api.command.ActorDelegation;
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
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.UserManagementService;
import com.ulticode.admin.port.UserProfilePort;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 0, check = false)
    private AccountManagementService accountManagementService;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private AccountQueryService accountQueryService;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 0, check = false)
    private AccountAdministrationService accountAdministrationService;

    private final UserProfilePort userProfilePort;
    private final AuditRecorder auditRecorder;
    private final AdminUserProjection adminUserProjection;

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.CREATE_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "#result.id")
    public AdminUserVO createUser(AdminCreateUserDTO dto) {
        checkQueryServiceAvailable();
        checkManagementServiceAvailable();

        RpcResult<AuthAccountDTO> usernameCheck = accountQueryService.getAccountByUsername(dto.getUsername());
        if (usernameCheck != null && usernameCheck.success() && usernameCheck.data() != null) {
            throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, "Username already exists");
        }

        if (StringUtils.hasText(dto.getEmail())) {
            RpcResult<AuthAccountDTO> emailCheck = accountQueryService.getAccountByEmail(dto.getEmail());
            if (emailCheck != null && emailCheck.success() && emailCheck.data() != null) {
                throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, "Email already exists");
            }
        }

        String role = StringUtils.hasText(dto.getRole()) ? dto.getRole() : "USER";
        String commandId = UUID.randomUUID().toString();
        CreateAccountCommand createCmd = new CreateAccountCommand(
                commandId,
                IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin", "admin", "admin create user"),
                TraceMetadata.EMPTY,
                dto.getUsername(),
                dto.getEmail(),
                dto.getPassword() != null ? dto.getPassword() : "DefaultPass123",
                role
        );

        RpcResult<AccountMutationDTO> createResult = accountManagementService.createAccount(createCmd);
        if (createResult == null || !createResult.success() || createResult.data() == null) {
            throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, "Failed to create account on Auth provider");
        }

        String newUserId = createResult.data().accountId();

        if (StringUtils.hasText(dto.getName())) {
            UpdateUserDTO profileDTO = new UpdateUserDTO();
            profileDTO.setName(dto.getName());
            userProfilePort.updateProfile(newUserId, profileDTO);
        }

        log.info("User created: {} by admin", newUserId);
        return adminUserProjection.getUserById(newUserId);
    }

    @Override
    @Transactional
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
            RpcResult<AuthAccountDTO> existingUsername = accountQueryService.getAccountByUsername(dto.getUsername());
            if (existingUsername != null && existingUsername.success() && existingUsername.data() != null) {
                throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, "Username already exists");
            }
        }

        if (StringUtils.hasText(dto.getEmail()) && !dto.getEmail().equals(current.email())) {
            RpcResult<AuthAccountDTO> existingEmail = accountQueryService.getAccountByEmail(dto.getEmail());
            if (existingEmail != null && existingEmail.success() && existingEmail.data() != null) {
                throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, "Email already exists");
            }
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
                new ActorDelegation("ADMIN", "admin", "admin", "admin update credentials"),
                TraceMetadata.EMPTY,
                id,
                newUsername,
                newEmail
        );
        accountManagementService.updateCredentials(updateCredsCmd);

        UpdateUserDTO profileDTO = new UpdateUserDTO();
        profileDTO.setName(dto.getName());
        profileDTO.setAvatar(dto.getAvatar());
        profileDTO.setBio(dto.getBio());
        profileDTO.setCompany(dto.getCompany());
        profileDTO.setGithub(dto.getGithub());
        profileDTO.setWebsite(dto.getWebsite());
        profileDTO.setLocation(dto.getLocation());
        profileDTO.setTwitter(dto.getTwitter());
        profileDTO.setPreferredLanguage(dto.getPreferredLanguage());
        userProfilePort.updateProfile(id, profileDTO);

        if (StringUtils.hasText(dto.getRole()) && !dto.getRole().equals(current.role())) {
            try {
                if (accountAdministrationService != null) {
                    ActorDelegation actor = new ActorDelegation("ADMIN", "admin", "admin", "admin user update");
                    String reqId = TraceIdUtil.current();
                    if (reqId == null || reqId.isBlank()) {
                        reqId = "t-" + UUID.randomUUID().toString();
                    }
                    String stableKey = "auth-role-update-" + reqId + "-" + id;
                    String commandId = UUID.nameUUIDFromBytes(stableKey.getBytes()).toString();
                    ChangeAuthorizationCommand command = new ChangeAuthorizationCommand(
                            commandId, IdMetadata.of(stableKey, null), actor, new TraceMetadata(reqId, null, null, null),
                            id, current.authzVersion(), dto.getRole(), Collections.emptySet(), "update user role"
                    );
                    accountAdministrationService.changeAuthorization(command);
                } else {
                    log.warn("AccountAdministrationService unavailable; role change for user {} skipped", id);
                }
            } catch (RuntimeException e) {
                log.warn("Role change failed for user {}: {}", id, e.getMessage());
            }
        }

        AuditContext.setNewValues(Map.of(
                "username", newUsername,
                "email", newEmail,
                "role", dto.getRole() != null ? dto.getRole() : current.role(),
                "isActive", dto.getIsActive() != null ? dto.getIsActive() : current.active()
        ));

        log.info("User updated: {}", id);
        return adminUserProjection.getUserById(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.BAN_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public AdminUserVO banUser(String id, String reason, String until) {
        checkQueryServiceAvailable();
        AuthAccountDTO current = getAccountOrThrow(id);

        ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin", "admin", "admin ban user"),
                TraceMetadata.EMPTY,
                id,
                current.authzVersion(),
                ChangeAccountStateCommand.AccountStateAction.BAN,
                reason
        );
        executeStateChange(command);
        log.info("User banned: {} - reason: {}", id, reason);
        return adminUserProjection.getUserById(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UNBAN_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public AdminUserVO unbanUser(String id) {
        checkQueryServiceAvailable();
        AuthAccountDTO current = getAccountOrThrow(id);

        ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin", "admin", "admin unban user"),
                TraceMetadata.EMPTY,
                id,
                current.authzVersion(),
                ChangeAccountStateCommand.AccountStateAction.UNBAN,
                "admin unban user"
        );
        executeStateChange(command);
        log.info("User unbanned: {}", id);
        return adminUserProjection.getUserById(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.DELETE_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public void deleteUser(String id) {
        checkManagementServiceAvailable();
        DeleteAccountCommand command = new DeleteAccountCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin", "admin", "admin delete user"),
                TraceMetadata.EMPTY,
                id,
                "admin delete user"
        );
        accountManagementService.deleteAccount(command);
        log.info("User deleted: {}", id);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.RESET_PASSWORD, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public void resetPassword(String id, String newPassword) {
        checkManagementServiceAvailable();
        AuditContext.setOldValues(Map.of("passwordChanged", false));
        AuditContext.setNewValues(Map.of("passwordChanged", true));

        ResetPasswordCommand command = new ResetPasswordCommand(
                UUID.randomUUID().toString(),
                IdMetadata.mint(),
                new ActorDelegation("ADMIN", "admin", "admin", "admin reset password"),
                TraceMetadata.EMPTY,
                id,
                newPassword,
                "admin reset password"
        );
        accountManagementService.resetPassword(command);
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
        if (accountAdministrationService != null) {
            RpcResult<AccountStateDTO> res = accountAdministrationService.changeState(command);
            if (res == null || !res.success()) {
                throw new BusinessException(AdminErrorCode.VALIDATION_FAILED, "Account state mutation failed");
            }
        }
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
}
