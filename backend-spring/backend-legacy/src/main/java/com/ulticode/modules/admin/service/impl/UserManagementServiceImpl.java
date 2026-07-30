package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ulticode.auth.api.command.ActorDelegation;
import com.ulticode.auth.api.command.ChangeAccountStateCommand;
import com.ulticode.auth.api.command.ChangeAuthorizationCommand;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.tracing.IdMetadata;
import com.ulticode.common.tracing.TraceMetadata;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.TraceIdUtil;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.common.util.PartialUpdate;
import com.ulticode.modules.admin.client.BackendAuthRoleAdminClient;
import com.ulticode.modules.admin.dto.AdminCreateUserDTO;
import com.ulticode.modules.admin.dto.AdminUpdateUserDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.UserManagementService;
import com.ulticode.modules.auth.account.AuthAccountPort;
import com.ulticode.modules.auth.service.AuthCutoverService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.port.UserProfilePort;
import com.ulticode.modules.user.dto.UpdateUserDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    private final AuthAccountPort accountPort;
    private final UserProfilePort userProfilePort;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;
    private final AdminUserProjection adminUserProjection;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;
    private final BackendAuthRoleAdminClient backendAuthRoleAdminClient;
    private final AuthCutoverService authCutoverService;

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.CREATE_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "#result.id")
    public AdminUserVO createUser(AdminCreateUserDTO dto) {
        User existing = accountPort.findByUsername(dto.getUsername()).orElse(null);
        if (existing != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Username already exists");
        }

        if (StringUtils.hasText(dto.getEmail())) {
            User existingEmail = accountPort.findByEmail(dto.getEmail()).orElse(null);
            if (existingEmail != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Email already exists");
            }
        }

        User user = new User();
        user.setId(uuidGenerator.newId());
        user.setUsername(dto.getUsername());
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        user.setIsBanned(false);
        user.setJoinedAt(LocalDateTime.now(clock));

        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        accountPort.create(user);

        if (StringUtils.hasText(dto.getRole()) && !"USER".equalsIgnoreCase(dto.getRole())) {
            try {
                if (authCutoverService != null) {
                    ActorDelegation actor = new ActorDelegation("ADMIN", "admin", "admin", "admin user create");
                    String reqId = TraceIdUtil.current();
                    if (reqId == null || reqId.isBlank()) {
                        reqId = "t-" + UUID.randomUUID().toString();
                    }
                    String stableKey = "auth-role-create-" + reqId + "-" + user.getId();
                    String commandId = UUID.nameUUIDFromBytes(stableKey.getBytes()).toString();
                    ChangeAuthorizationCommand command = new ChangeAuthorizationCommand(
                            commandId, IdMetadata.of(stableKey, null), actor, new TraceMetadata(reqId, null, null, null),
                            user.getId(), 0L, dto.getRole(), Collections.emptySet(), "create user role"
                    );
                    authCutoverService.changeAuthorization(command);
                } else {
                    backendAuthRoleAdminClient.changeRole(user.getId(), dto.getRole());
                }
            } catch (RuntimeException e) {
                log.warn("Role change failed for new user {}: {}", user.getId(), e.getMessage());
            }
        }

        log.info("User created: {} by admin", user.getId());
        return adminUserProjection.getUserById(user.getId());
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UPDATE_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public AdminUserVO updateUser(String id, AdminUpdateUserDTO dto) {
        User user = accountPort.findById(id).orElse(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (StringUtils.hasText(dto.getUsername()) && !dto.getUsername().equals(user.getUsername())) {
            User existingUsername = accountPort.findByUsername(dto.getUsername()).orElse(null);
            if (existingUsername != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Username already exists");
            }
        }

        if (StringUtils.hasText(dto.getEmail()) && !dto.getEmail().equals(user.getEmail())) {
            User existingEmail = accountPort.findByEmail(dto.getEmail()).orElse(null);
            if (existingEmail != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Email already exists");
            }
        }

        AuditContext.setOldValues(Map.of(
            "username", user.getUsername(),
            "name", user.getName(),
            "email", user.getEmail(),
            "role", user.getRole(),
            "isActive", user.getIsActive()
        ));

        accountPort.updateAccountCredentials(id, dto.getUsername(), dto.getEmail(), null);

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

        if (StringUtils.hasText(dto.getRole())) {
            try {
                if (authCutoverService != null) {
                    ActorDelegation actor = new ActorDelegation("ADMIN", "admin", "admin", "admin user update");
                    String reqId = TraceIdUtil.current();
                    if (reqId == null || reqId.isBlank()) {
                        reqId = "t-" + UUID.randomUUID().toString();
                    }
                    String stableKey = "auth-role-update-" + reqId + "-" + id;
                    String commandId = UUID.nameUUIDFromBytes(stableKey.getBytes()).toString();
                    ChangeAuthorizationCommand command = new ChangeAuthorizationCommand(
                            commandId, IdMetadata.of(stableKey, null), actor, new TraceMetadata(reqId, null, null, null),
                            id, 0L, dto.getRole(), Collections.emptySet(), "update user role"
                    );
                    authCutoverService.changeAuthorization(command);
                } else {
                    backendAuthRoleAdminClient.changeRole(id, dto.getRole());
                }
            } catch (RuntimeException e) {
                log.warn("Role change failed for user {}: {}", id, e.getMessage());
            }
        }

        AuditContext.setNewValues(Map.of(
            "username", dto.getUsername() != null ? dto.getUsername() : user.getUsername(),
            "name", dto.getName() != null ? dto.getName() : user.getName(),
            "email", dto.getEmail() != null ? dto.getEmail() : user.getEmail(),
            "role", dto.getRole() != null ? dto.getRole() : user.getRole(),
            "isActive", dto.getIsActive() != null ? dto.getIsActive() : user.getIsActive()
        ));

        log.info("User updated: {}", id);
        return adminUserProjection.getUserById(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.BAN_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public AdminUserVO banUser(String id, String reason, String until) {
        executeBan(id, reason, until);
        log.info("User banned: {} - reason: {}", id, reason);
        return adminUserProjection.getUserById(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.UNBAN_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public AdminUserVO unbanUser(String id) {
        executeUnban(id);
        log.info("User unbanned: {}", id);
        return adminUserProjection.getUserById(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.DELETE_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public void deleteUser(String id) {
        executeDelete(id);
        log.info("User deleted: {}", id);
    }

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.RESET_PASSWORD, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "id")
    public void resetPassword(String id, String newPassword) {
        executeResetPassword(id, newPassword);
        log.info("Password reset for user: {}", id);
    }

    private void executeResetPassword(String id, String newPassword) {
        User user = accountPort.findById(id).orElse(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of("passwordChanged", false));
        AuditContext.setNewValues(Map.of("passwordChanged", true));

        String hashedPassword = passwordEncoder.encode(newPassword);
        accountPort.updatePassword(id, hashedPassword);
    }

    private void executeBan(String id, String reason, String until) {
        User user = accountPort.findById(id).orElse(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of(
            "isBanned", user.getIsBanned(),
            "bannedReason", user.getBannedReason() != null ? user.getBannedReason() : ""
        ));

        if (authCutoverService != null) {
            ActorDelegation actor = new ActorDelegation("ADMIN", "admin", "admin", "ban user");
            String reqId = TraceIdUtil.current();
            if (reqId == null || reqId.isBlank()) {
                reqId = "t-" + UUID.randomUUID().toString();
            }
            String stableKey = "auth-ban-" + reqId + "-" + id;
            String commandId = UUID.nameUUIDFromBytes(stableKey.getBytes()).toString();
            ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                    commandId, IdMetadata.of(stableKey, null), actor, new TraceMetadata(reqId, null, null, null),
                    id, 0L, ChangeAccountStateCommand.AccountStateAction.BAN, reason
            );
            authCutoverService.changeState(command);
        } else {
            accountPort.updateBanStatus(id, true, reason);
        }

        AuditContext.setNewValues(Map.of(
            "isBanned", true,
            "bannedReason", reason != null ? reason : ""
        ));
    }

    private void executeUnban(String id) {
        User user = accountPort.findById(id).orElse(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of(
            "isBanned", user.getIsBanned(),
            "bannedReason", user.getBannedReason() != null ? user.getBannedReason() : ""
        ));

        if (authCutoverService != null) {
            ActorDelegation actor = new ActorDelegation("ADMIN", "admin", "admin", "unban user");
            String reqId = TraceIdUtil.current();
            if (reqId == null || reqId.isBlank()) {
                reqId = "t-" + UUID.randomUUID().toString();
            }
            String stableKey = "auth-unban-" + reqId + "-" + id;
            String commandId = UUID.nameUUIDFromBytes(stableKey.getBytes()).toString();
            ChangeAccountStateCommand command = new ChangeAccountStateCommand(
                    commandId, IdMetadata.of(stableKey, null), actor, new TraceMetadata(reqId, null, null, null),
                    id, 0L, ChangeAccountStateCommand.AccountStateAction.UNBAN, "unban"
            );
            authCutoverService.changeState(command);
        } else {
            accountPort.updateBanStatus(id, false, null);
        }

        AuditContext.setNewValues(Map.of("isBanned", false, "bannedReason", ""));
    }

    private void executeDelete(String id) {
        User user = accountPort.findById(id).orElse(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of("username", user.getUsername()));
        AuditContext.setNewValues(Map.of("deleted", true));

        accountPort.deleteAccount(id);
    }

    private void recordLifecycleAudit(String id, String action) {
        auditRecorder.recordForUser(
            action,
            AuditVocabulary.ENTITY_USER,
            id, id,
            AuditContext.getOldValues(),
            AuditContext.getNewValues());
        AuditContext.clear();
    }

    @Override
    @Transactional
    public List<BanResult> bulkBan(List<String> ids, String reason) {
        List<BanResult> results = new ArrayList<>();

        for (String id : ids) {
            try {
                executeBan(id, reason, null);
                recordLifecycleAudit(id, AuditVocabulary.BAN_USER);
                results.add(new BanResult(id, true, null));
            } catch (RuntimeException e) {
                AuditContext.clear();
                log.error("Failed to ban user {}: {}", id, e.getMessage());
                results.add(new BanResult(id, false, e.getMessage()));
            }
        }

        return results;
    }

    @Override
    @Transactional
    public List<BanResult> bulkUnban(List<String> ids) {
        List<BanResult> results = new ArrayList<>();

        for (String id : ids) {
            try {
                executeUnban(id);
                recordLifecycleAudit(id, AuditVocabulary.UNBAN_USER);
                results.add(new BanResult(id, true, null));
            } catch (RuntimeException e) {
                AuditContext.clear();
                log.error("Failed to unban user {}: {}", id, e.getMessage());
                results.add(new BanResult(id, false, e.getMessage()));
            }
        }

        return results;
    }

    @Override
    @Transactional
    public List<DeleteResult> bulkDelete(List<String> ids) {
        List<DeleteResult> results = new ArrayList<>();

        for (String id : ids) {
            try {
                executeDelete(id);
                recordLifecycleAudit(id, AuditVocabulary.DELETE_USER);
                results.add(new DeleteResult(id, true, null));
            } catch (RuntimeException e) {
                AuditContext.clear();
                log.error("Failed to delete user {}: {}", id, e.getMessage());
                results.add(new DeleteResult(id, false, e.getMessage()));
            }
        }

        return results;
    }
}
