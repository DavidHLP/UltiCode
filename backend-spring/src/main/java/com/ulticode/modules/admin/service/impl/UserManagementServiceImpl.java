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
import com.ulticode.modules.admin.dto.AdminCreateUserDTO;
import com.ulticode.modules.admin.dto.AdminUpdateUserDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.projection.AdminUserProjection;
import com.ulticode.modules.admin.service.UserManagementService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
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

    private final UserMapper userMapper;
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

    @Override
    @Transactional
    @Audited(action = AuditVocabulary.CREATE_USER, entityType = AuditVocabulary.ENTITY_USER, userIdFrom = "#result.id")
    public AdminUserVO createUser(AdminCreateUserDTO dto) {
        // 用户名唯一性校验
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (existing != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Username already exists");
        }

        // 邮箱唯一性校验
        if (StringUtils.hasText(dto.getEmail())) {
            User existingEmail = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, dto.getEmail()));
            if (existingEmail != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Email already exists");
            }
        }

        User user = new User();
        user.setId(uuidGenerator.newId());
        user.setUsername(dto.getUsername());
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole() != null ? dto.getRole() : "USER");
        user.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        user.setIsBanned(false);
        user.setJoinedAt(LocalDateTime.now(clock));

        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        userMapper.insert(user);
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
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 用户名唯一性校验（排除当前用户）
        if (StringUtils.hasText(dto.getUsername()) && !dto.getUsername().equals(user.getUsername())) {
            User existingUsername = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
            if (existingUsername != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Username already exists");
            }
        }

        // 邮箱唯一性校验（排除当前用户）
        if (StringUtils.hasText(dto.getEmail()) && !dto.getEmail().equals(user.getEmail())) {
            User existingEmail = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, dto.getEmail()));
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

        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, id);

        // Partial-update set clauses — null / blank values are silently
        // skipped, so the row's existing value is preserved. The wrapper
        // pattern accumulates the SET clauses and applies them in one UPDATE.
        PartialUpdate.setIfPresentTextWrapper(wrapper, dto, AdminUpdateUserDTO::getUsername, User::getUsername);
        PartialUpdate.setIfPresentTextWrapper(wrapper, dto, AdminUpdateUserDTO::getName, User::getName);
        PartialUpdate.setIfPresentTextWrapper(wrapper, dto, AdminUpdateUserDTO::getEmail, User::getEmail);
        PartialUpdate.setIfPresentTextWrapper(wrapper, dto, AdminUpdateUserDTO::getRole, User::getRole);
        PartialUpdate.setIfPresentWrapper(wrapper, dto, AdminUpdateUserDTO::getIsActive, User::getIsActive);
        PartialUpdate.setIfPresentTextWrapper(wrapper, dto, AdminUpdateUserDTO::getAvatar, User::getAvatar);
        PartialUpdate.setIfPresentTextWrapper(wrapper, dto, AdminUpdateUserDTO::getBio, User::getBio);
        PartialUpdate.setIfPresentTextWrapper(wrapper, dto, AdminUpdateUserDTO::getCompany, User::getCompany);
        PartialUpdate.setIfPresentTextWrapper(wrapper, dto, AdminUpdateUserDTO::getGithub, User::getGithub);
        PartialUpdate.setIfPresentTextWrapper(wrapper, dto, AdminUpdateUserDTO::getWebsite, User::getWebsite);
        PartialUpdate.setIfPresentTextWrapper(wrapper, dto, AdminUpdateUserDTO::getLocation, User::getLocation);
        PartialUpdate.setIfPresentTextWrapper(wrapper, dto, AdminUpdateUserDTO::getTwitter, User::getTwitter);
        PartialUpdate.setIfPresentTextWrapper(wrapper, dto, AdminUpdateUserDTO::getPreferredLanguage, User::getPreferredLanguage);

        userMapper.update(null, wrapper);

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
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of("passwordChanged", false));
        AuditContext.setNewValues(Map.of("passwordChanged", true));

        String hashedPassword = passwordEncoder.encode(newPassword);
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, id)
                .set(User::getPassword, hashedPassword);

        userMapper.update(null, wrapper);

        log.info("Password reset for user: {}", id);
    }

    /**
     * Core ban mutation shared by {@link #banUser} and {@link #bulkBan}.
     * Populates {@link AuditContext} old/new values so the single path (via
     * the {@code @Audited} aspect) and the bulk path (via {@link AuditRecorder})
     * emit identical audit semantics — the bulk path cannot rely on the
     * aspect because a self-invoked method bypasses the Spring proxy.
     */
    private void executeBan(String id, String reason, String until) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of(
            "isBanned", user.getIsBanned(),
            "bannedReason", user.getBannedReason() != null ? user.getBannedReason() : ""
        ));

        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, id)
                .set(User::getIsBanned, true)
                .set(User::getBannedReason, reason);

        if (StringUtils.hasText(until)) {
            try {
                wrapper.set(User::getBannedUntil, LocalDateTime.parse(until));
            } catch (DateTimeParseException e) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Invalid banned_until date format: " + until);
            }
        }

        userMapper.update(null, wrapper);

        AuditContext.setNewValues(Map.of(
            "isBanned", true,
            "bannedReason", reason != null ? reason : ""
        ));
    }

    /**
     * Core unban mutation shared by {@link #unbanUser} and {@link #bulkUnban}.
     */
    private void executeUnban(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of(
            "isBanned", user.getIsBanned(),
            "bannedReason", user.getBannedReason() != null ? user.getBannedReason() : ""
        ));

        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(User::getId, id)
                .set(User::getIsBanned, false)
                .set(User::getBannedReason, null)
                .set(User::getBannedUntil, null);

        userMapper.update(null, wrapper);

        AuditContext.setNewValues(Map.of("isBanned", false, "bannedReason", ""));
    }

    /**
     * Core delete mutation shared by {@link #deleteUser} and
     * {@link #bulkDelete}. Records the same {@code deleted:true} new-value
     * shape as the single path so audit entries do not drift between flows.
     */
    private void executeDelete(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of("username", user.getUsername()));
        AuditContext.setNewValues(Map.of("deleted", true));

        userMapper.deleteById(id);
    }

    @Override
    @Transactional
    public List<BanResult> bulkBan(List<String> ids, String reason) {
        List<BanResult> results = new ArrayList<>();

        for (String id : ids) {
            try {
                executeBan(id, reason, null);
                // Bulk operations cannot rely on the @Audited aspect — a
                // self-invoked banUser bypasses the proxy and would emit no
                // audit. Emit through the same AuditRecorder policy the
                // aspect uses, reading the values executeBan staged.
                auditRecorder.recordForUser(
                    AuditVocabulary.BAN_USER,
                    AuditVocabulary.ENTITY_USER,
                    id, id,
                    AuditContext.getOldValues(),
                    AuditContext.getNewValues());
                AuditContext.clear();
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
                auditRecorder.recordForUser(
                    AuditVocabulary.UNBAN_USER,
                    AuditVocabulary.ENTITY_USER,
                    id, id,
                    AuditContext.getOldValues(),
                    AuditContext.getNewValues());
                AuditContext.clear();
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
                auditRecorder.recordForUser(
                    AuditVocabulary.DELETE_USER,
                    AuditVocabulary.ENTITY_USER,
                    id, id,
                    AuditContext.getOldValues(),
                    AuditContext.getNewValues());
                AuditContext.clear();
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
