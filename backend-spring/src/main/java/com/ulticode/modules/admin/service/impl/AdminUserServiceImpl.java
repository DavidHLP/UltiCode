package com.ulticode.modules.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.common.annotation.Audited;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.util.AuditActionUtil;
import com.ulticode.common.util.AuditContext;
import com.ulticode.common.util.AuditHelper;
import com.ulticode.modules.admin.dto.AdminCreateUserDTO;
import com.ulticode.modules.admin.dto.AdminUpdateUserDTO;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.service.AdminUserService;
import com.ulticode.modules.permission.entity.RolePermission;
import com.ulticode.modules.permission.entity.UserPermission;
import com.ulticode.modules.permission.mapper.RolePermissionMapper;
import com.ulticode.modules.permission.service.PermissionService;
import com.ulticode.modules.solution.mapper.SolutionMapper;
import com.ulticode.modules.submission.mapper.SubmissionMapper;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of AdminUserService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditHelper auditHelper;
    private final SubmissionMapper submissionMapper;
    private final SolutionMapper solutionMapper;
    private final PermissionService permissionService;
    private final RolePermissionMapper rolePermissionMapper;

    @Override
    public PageResult<AdminUserVO> getUsers(AdminUserQueryDTO query) {
        int page = query.getPage() != null && query.getPage() > 0 ? query.getPage() : 1;
        int limit = query.getLimit() != null && query.getLimit() > 0 ? Math.min(query.getLimit(), 100) : 10;

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // Search filter
        if (StringUtils.hasText(query.getSearch())) {
            String search = "%" + query.getSearch() + "%";
            wrapper.and(w -> w
                    .like(User::getUsername, search)
                    .or().like(User::getEmail, search)
                    .or().like(User::getName, search));
        }

        // Role filter
        if (StringUtils.hasText(query.getRole())) {
            wrapper.eq(User::getRole, query.getRole());
        }

        // Active status filter
        if (query.getIsActive() != null) {
            wrapper.eq(User::getIsActive, query.getIsActive());
        }

        // Banned status filter
        if (query.getIsBanned() != null) {
            wrapper.eq(User::getIsBanned, query.getIsBanned());
        }

        // Sorting
        boolean isAsc = "asc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "joinedAt";
        switch (sortBy) {
            case "username" -> wrapper.orderBy(true, isAsc, User::getUsername);
            case "email" -> wrapper.orderBy(true, isAsc, User::getEmail);
            case "lastLoginAt" -> wrapper.orderBy(true, isAsc, User::getLastLoginAt);
            default -> wrapper.orderBy(true, isAsc, User::getJoinedAt);
        }

        Page<User> userPage = new Page<>(page, limit);
        Page<User> result = userMapper.selectPage(userPage, wrapper);

        List<AdminUserVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), page, limit);
    }

    @Override
    public AdminUserVO getUserById(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        return toVO(user);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.BAN_USER, entityType = AuditActionUtil.ENTITY_USER, userIdFrom = "id")
    public AdminUserVO banUser(String id, String reason, String until) {
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

        log.info("User banned: {} - reason: {}", id, reason);
        return getUserById(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.UNBAN_USER, entityType = AuditActionUtil.ENTITY_USER, userIdFrom = "id")
    public AdminUserVO unbanUser(String id) {
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

        log.info("User unbanned: {}", id);
        return getUserById(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.CREATE_USER, entityType = AuditActionUtil.ENTITY_USER, userIdFrom = "#result.id")
    public AdminUserVO createUser(AdminCreateUserDTO dto) {
        // Check username uniqueness
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (existing != null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Username already exists");
        }

        // Check email uniqueness
        if (StringUtils.hasText(dto.getEmail())) {
            User existingEmail = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, dto.getEmail()));
            if (existingEmail != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Email already exists");
            }
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setUsername(dto.getUsername());
        user.setName(dto.getName());
        user.setEmail(dto.getEmail());
        user.setRole(dto.getRole() != null ? dto.getRole() : "USER");
        user.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        user.setIsBanned(false);
        user.setJoinedAt(LocalDateTime.now());

        if (StringUtils.hasText(dto.getPassword())) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        userMapper.insert(user);
        log.info("User created: {} by admin", user.getId());
        return toVO(user);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.UPDATE_USER, entityType = AuditActionUtil.ENTITY_USER, userIdFrom = "id")
    public AdminUserVO updateUser(String id, AdminUpdateUserDTO dto) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // Check username uniqueness (excluding current user)
        if (StringUtils.hasText(dto.getUsername()) && !dto.getUsername().equals(user.getUsername())) {
            User existingUsername = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
            if (existingUsername != null) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Username already exists");
            }
        }

        // Check email uniqueness (excluding current user)
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

        if (StringUtils.hasText(dto.getUsername())) {
            wrapper.set(User::getUsername, dto.getUsername());
        }
        if (StringUtils.hasText(dto.getName())) {
            wrapper.set(User::getName, dto.getName());
        }
        if (StringUtils.hasText(dto.getEmail())) {
            wrapper.set(User::getEmail, dto.getEmail());
        }
        if (StringUtils.hasText(dto.getRole())) {
            wrapper.set(User::getRole, dto.getRole());
        }
        if (dto.getIsActive() != null) {
            wrapper.set(User::getIsActive, dto.getIsActive());
        }
        if (StringUtils.hasText(dto.getAvatar())) {
            wrapper.set(User::getAvatar, dto.getAvatar());
        }
        if (StringUtils.hasText(dto.getBio())) {
            wrapper.set(User::getBio, dto.getBio());
        }
        if (StringUtils.hasText(dto.getCompany())) {
            wrapper.set(User::getCompany, dto.getCompany());
        }
        if (StringUtils.hasText(dto.getGithub())) {
            wrapper.set(User::getGithub, dto.getGithub());
        }
        if (StringUtils.hasText(dto.getWebsite())) {
            wrapper.set(User::getWebsite, dto.getWebsite());
        }
        if (StringUtils.hasText(dto.getLocation())) {
            wrapper.set(User::getLocation, dto.getLocation());
        }
        if (StringUtils.hasText(dto.getTwitter())) {
            wrapper.set(User::getTwitter, dto.getTwitter());
        }
        if (StringUtils.hasText(dto.getPreferredLanguage())) {
            wrapper.set(User::getPreferredLanguage, dto.getPreferredLanguage());
        }

        userMapper.update(null, wrapper);

        AuditContext.setNewValues(Map.of(
            "username", dto.getUsername() != null ? dto.getUsername() : user.getUsername(),
            "name", dto.getName() != null ? dto.getName() : user.getName(),
            "email", dto.getEmail() != null ? dto.getEmail() : user.getEmail(),
            "role", dto.getRole() != null ? dto.getRole() : user.getRole(),
            "isActive", dto.getIsActive() != null ? dto.getIsActive() : user.getIsActive()
        ));

        log.info("User updated: {}", id);
        return getUserById(id);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.DELETE_USER, entityType = AuditActionUtil.ENTITY_USER, userIdFrom = "id")
    public void deleteUser(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        AuditContext.setOldValues(Map.of("username", user.getUsername()));
        AuditContext.setNewValues(Map.of("deleted", true));

        userMapper.deleteById(id);

        log.info("User deleted: {}", id);
    }

    @Override
    @Transactional
    @Audited(action = AuditActionUtil.RESET_PASSWORD, entityType = AuditActionUtil.ENTITY_USER, userIdFrom = "id")
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

    @Override
    @Transactional
    public List<BanResult> bulkBan(List<String> ids, String reason) {
        List<BanResult> results = new ArrayList<>();

        for (String id : ids) {
            try {
                banUser(id, reason, null);
                results.add(new BanResult(id, true, null));
            } catch (RuntimeException e) {
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
                unbanUser(id);
                results.add(new BanResult(id, true, null));
            } catch (RuntimeException e) {
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
                User user = userMapper.selectById(id);
                int deleted = userMapper.deleteById(id);
                if (deleted > 0) {
                    auditHelper.logForUser(
                        AuditActionUtil.DELETE_USER,
                        AuditActionUtil.ENTITY_USER,
                        id,
                        id,
                        Map.of("username", user != null ? user.getUsername() : "unknown"),
                        null
                    );
                    results.add(new DeleteResult(id, true, null));
                } else {
                    results.add(new DeleteResult(id, false, "User not found"));
                }
            } catch (RuntimeException e) {
                log.error("Failed to delete user {}: {}", id, e.getMessage());
                results.add(new DeleteResult(id, false, e.getMessage()));
            }
        }

        return results;
    }

    /**
     * Convert User entity to AdminUserVO
     */
    private AdminUserVO toVO(User user) {
        if (user == null) {
            return null;
        }

        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setName(user.getName());
        vo.setEmail(user.getEmail());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setIsActive(user.getIsActive());
        vo.setIsBanned(user.getIsBanned());
        vo.setBanReason(user.getBannedReason());
        vo.setBannedUntil(user.getBannedUntil());
        vo.setJoinedAt(user.getJoinedAt());
        vo.setLastLoginAt(user.getLastLoginAt());

        // Populate stats
        AdminUserVO.UserStatsInfo stats = new AdminUserVO.UserStatsInfo();
        Long totalSubmissions = submissionMapper.countByUserId(user.getId());
        Long acceptedSubmissions = submissionMapper.countAcceptedProblemsByUserId(user.getId());
        Long totalSolutions = solutionMapper.countByUserId(user.getId());
        Integer streak = submissionMapper.calculateStreak(user.getId());
        stats.setTotalSubmissions(totalSubmissions != null ? totalSubmissions.intValue() : 0);
        stats.setAcceptedSubmissions(acceptedSubmissions != null ? acceptedSubmissions.intValue() : 0);
        stats.setTotalSolutions(totalSolutions != null ? totalSolutions.intValue() : 0);
        stats.setStreak(streak != null ? streak : 0);
        vo.setStats(stats);

        // Populate permissions
        List<AdminUserVO.PermissionInfo> permissions = new ArrayList<>();

        // Role-based permissions
        if (StringUtils.hasText(user.getRole())) {
            List<RolePermission> rolePerms = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>()
                    .eq(RolePermission::getRole, user.getRole()));
            for (RolePermission rp : rolePerms) {
                AdminUserVO.PermissionInfo info = new AdminUserVO.PermissionInfo();
                info.setAction(rp.getAction());
                info.setResource(rp.getResource());
                info.setSource("role");
                info.setExpiresAt(null);
                permissions.add(info);
            }
        }

        // Direct user permissions
        List<UserPermission> userPerms = permissionService.getUserPermissions(user.getId());
        for (UserPermission up : userPerms) {
            AdminUserVO.PermissionInfo info = new AdminUserVO.PermissionInfo();
            info.setAction(up.getAction());
            info.setResource(up.getResource());
            info.setSource("direct");
            info.setExpiresAt(null);
            permissions.add(info);
        }
        vo.setPermissions(permissions);

        return vo;
    }
}
