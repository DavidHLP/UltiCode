package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.audit.AuditRecorder;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.audit.AuditVocabulary;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.uuid.UuidGenerator;
import com.ulticode.common.util.PartialUpdate;
import com.ulticode.modules.admin.client.BackendAuthRoleAdminClient;
import com.ulticode.modules.admin.dto.AdminCreateUserDTO;
import com.ulticode.modules.admin.dto.AdminUpdateUserDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.UserManagementService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.auth.account.AuthAccountPort;
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
import java.util.List;
import java.util.Map;

/**
 * 用户写操作服务实现：CRUD、封禁、批量操作。
 *
 * <p>从原 {@code AdminUserServiceImpl}（611 行）拆分而来（架构评审 Candidate 1）。
 * 权限授予 / 撤销逻辑移至 {@link UserPermissionServiceImpl}，避免两类不相关的方法共享同一接口。
 *
 * <p><b>ADR-0011 Stage 2 更新</b>：所有读路径（{@code getUsers} 列表读、
 * {@code getUserById} 详情读 + stats + permissions enrichment）已迁移至
 * {@link AdminUserProjection}。本服务现在只承担写状态机：
 * <ul>
 *   <li>createUser / updateUser / deleteUser</li>
 *   <li>banUser / unbanUser / resetPassword</li>
 *   <li>bulkBan / bulkUnban / bulkDelete</li>
 * </ul>
 * 写方法返回的 {@link AdminUserVO} 通过委托
 * {@link AdminUserProjection#getUserById(String)} 组合而成 &mdash; 避免在两处
 * 复制 entity&rarr;VO 规则，并保留写后立即看到最新 stats + permissions 快照的契约。
 *
 * <p>跨模块依赖（{@code AdminUserStatsReadPort}、{@code RolePermissionMapper}、
 * {@code PermissionService}、{@code RolePermission} / {@code UserPermission} 实体）
 * 已迁出至 projection；本类不再导入它们。
 *
 * <p>{@link AdminUserProjection#getUserById(String)} 同时被
 * {@link UserPermissionServiceImpl} 在授权变更后调用，以返回最新的
 * {@link AdminUserVO}（含 stats 与 permissions 快照）。该读路径是写后 / 授权后
 * 的公共协作点，由 projection 单一拥有。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

        private final AuthAccountPort accountPort;
    private final UserProfilePort userProfilePort;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder auditRecorder;
    /**
     * Read-side deep module used to compose the post-write VO. After ADR-0011
     * Stage 2 the entity&rarr;VO shaping + stats / permissions enrichment
     * lives behind this seam; this service keeps writes only.
     */
    private final AdminUserProjection adminUserProjection;
    private final Clock clock;
    private final UuidGenerator uuidGenerator;
    /**
     * P2-RBAC-001: HTTP client that forwards role changes to
     * {@code backend-auth}'s owner-only command surface. The legacy
     * no longer writes to {@code users.role} directly when the admin
     * makes an explicit role choice; the system-default
     * ({@code "USER"} on create) remains a local write because
     * {@code users.role} is NOT NULL with no DEFAULT — a follow-up
     * migration is the proper long-term fix (see DECISIONS ADR for
     * the deferred user-creation refactor).
     */
    private final BackendAuthRoleAdminClient backendAuthRoleAdminClient;

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.CREATE_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "#result.id")
    public AdminUserVO createUser(AdminCreateUserDTO dto) {
        // 用户名唯一性校验
        User existing = accountPort.findByUsername(dto.getUsername()).orElse(null);
        if (existing != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Username already exists");
        }

        // 邮箱唯一性校验
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
        // P2-RBAC-001: the system-default role "USER" remains a local
        // write because users.role is NOT NULL with no DEFAULT. If the
        // admin picks a non-USER role at create time, the explicit
        // choice is forwarded to backend-auth after the local insert.
        user.setRole("USER");
        user.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        user.setIsBanned(false);
        user.setJoinedAt(LocalDateTime.now(clock));

        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        accountPort.create(user);

        // P2-RBAC-001: route the admin's non-default role choice
        // through backend-auth. Best-effort: a backend-auth outage
        // logs a warning and leaves the new user at role=USER; an
        // admin can re-run the role change via updateUser.
        if (StringUtils.hasText(dto.getRole()) && !"USER".equalsIgnoreCase(dto.getRole())) {
            try {
                backendAuthRoleAdminClient.changeRole(user.getId(), dto.getRole());
            } catch (RuntimeException e) {
                log.warn("Backend-auth role change failed for new user {}: {} (user created at role=USER; role pending)",
                        user.getId(), e.getMessage());
            }
        }

        log.info("User created: {} by admin", user.getId());
        // ADR-0011 Stage 2: post-write VO composed via the projection so the
        // admin UI sees the freshly created user's role permission snapshot
        // (consistent with update / ban / unban write paths).
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

        // 用户名唯一性校验（排除当前用户）
        if (StringUtils.hasText(dto.getUsername()) && !dto.getUsername().equals(user.getUsername())) {
            User existingUsername = accountPort.findByUsername(dto.getUsername()).orElse(null);
            if (existingUsername != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Username already exists");
            }
        }

        // 邮箱唯一性校验（排除当前用户）
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

        // Account credentials update (username / email) via AuthAccountPort
        accountPort.updateAccountCredentials(id, dto.getUsername(), dto.getEmail(), null);

        // Profile attributes update via UserProfilePort
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

        // P2-RBAC-001: forward the admin's role choice to backend-auth.
        // The local write above has already committed; a backend-auth
        // failure is logged but does not roll back the local profile
        // update, so the admin sees a successful profile update with a
        // warning that the role change is pending. The alternative
        // (call backend-auth first) is worse because a backend-auth
        // outage would block the rest of the profile update.
        if (StringUtils.hasText(dto.getRole())) {
            try {
                backendAuthRoleAdminClient.changeRole(id, dto.getRole());
            } catch (RuntimeException e) {
                log.warn("Backend-auth role change failed for user {}: {} (profile change preserved; role pending)",
                        id, e.getMessage());
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

    /**
     * Core password-reset mutation. Mirrors executeBan / executeUnban /
     * executeDelete so every single lifecycle mutation shares one shape:
     * the {@code @Audited} public method delegates to a private core that
     * owns the mutation and stages {@link AuditContext} old/new values.
     */
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

    /**
     * Core ban mutation shared by {@link #banUser} and {@link #bulkBan}.
     * Populates {@link AuditContext} old/new values so the single path (via
     * the {@code @Audited} aspect) and the bulk path (via {@link AuditRecorder})
     * emit identical audit semantics — the bulk path cannot rely on the
     * aspect because a self-invoked method bypasses the Spring proxy.
     */
    private void executeBan(String id, String reason, String until) {
        User user = accountPort.findById(id).orElse(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of(
            "isBanned", user.getIsBanned(),
            "bannedReason", user.getBannedReason() != null ? user.getBannedReason() : ""
        ));

        accountPort.updateBanStatus(id, true, reason);

        AuditContext.setNewValues(Map.of(
            "isBanned", true,
            "bannedReason", reason != null ? reason : ""
        ));
    }

    /**
     * Core unban mutation shared by {@link #unbanUser} and {@link #bulkUnban}.
     */
    private void executeUnban(String id) {
        User user = accountPort.findById(id).orElse(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of(
            "isBanned", user.getIsBanned(),
            "bannedReason", user.getBannedReason() != null ? user.getBannedReason() : ""
        ));

        accountPort.updateBanStatus(id, false, null);

        AuditContext.setNewValues(Map.of("isBanned", false, "bannedReason", ""));
    }

    /**
     * Core delete mutation shared by {@link #deleteUser} and
     * {@link #bulkDelete}. Records the same {@code deleted:true} new-value
     * shape as the single path so audit entries do not drift between flows.
     */
    private void executeDelete(String id) {
        User user = accountPort.findById(id).orElse(null);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of("username", user.getUsername()));
        AuditContext.setNewValues(Map.of("deleted", true));

        accountPort.deleteAccount(id);
    }

    /**
     * Emit the lifecycle audit staged by an {@code execute*} core and clear
     * the thread-local in one place. Bulk operations reach this path directly
     * because a self-invocation of the {@code @Audited} public method bypasses
     * the Spring proxy; centralizing record + clear keeps future code between
     * {@code execute*} and record from leaking {@link AuditContext} across
     * loop iterations.
     */
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
                // Bulk ops bypass the @Audited aspect (self-invocation), so
                // emit through the same AuditRecorder policy the aspect uses.
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
