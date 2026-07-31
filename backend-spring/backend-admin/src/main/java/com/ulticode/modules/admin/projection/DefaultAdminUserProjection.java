package com.ulticode.modules.admin.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ulticode.auth.api.dto.PermissionEntry;
import com.ulticode.auth.api.service.RoleTemplateService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.port.AdminUserStatsReadPort;
import com.ulticode.modules.auth.service.AuthCutoverService;
import com.ulticode.modules.user.entity.User;
import com.ulticode.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Default (and only) adapter for {@link AdminUserProjection}. Owns every
 * entity-to-VO projection rule and read-side aggregation for the admin user
 * surface &mdash; see the interface javadoc for why this is a deep module.
 *
 * <p>All methods are pure reads; none mutate user state. The detail read
 * ({@link #getUserById}) is the collaboration point used by both
 * {@link com.ulticode.modules.admin.service.UserManagementService} (after a
 * write) and {@link com.ulticode.modules.admin.service.UserPermissionService}
 * (after a grant / revoke) to compose the post-mutation VO.
 *
 * <p><b>Permission source migration (P7-RETIRE-PERMISSION-001):</b>
 * Role-template permissions are now fetched via Dubbo RPC
 * ({@link RoleTemplateService}); direct user permissions are read from the
 * authorization snapshot's {@code permissionEntries} via
 * {@link AuthCutoverService#getSnapshot}. The legacy
 * {@code RolePermissionMapper}, {@code PermissionService}, and their entity
 * types are no longer imported here.
 *
 * <p><b>List vs. detail asymmetry (intentional):</b> the list path
 * ({@link #getUsers}) deliberately omits stats / permissions enrichment to
 * keep the paginated read N+1-safe across a page of users. Only the
 * single-row detail path ({@link #getUserById}) pays the enrichment cost.
 *
 * @author ulticode
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAdminUserProjection implements AdminUserProjection {

    private final UserMapper userMapper;
    private final AdminUserStatsReadPort userStatsReadPort;
    private final AuthCutoverService authCutoverService;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private RoleTemplateService roleTemplateService;

    // ------------------------------------------------------------------
    // Paginated list read (query build + entity->VO shaping, NO enrichment)
    // ------------------------------------------------------------------

    @Override
    public PageResult<AdminUserVO> getUsers(AdminUserQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        // 搜索过滤 — username / email / name 三列 OR
        if (StringUtils.hasText(query.getSearch())) {
            String search = "%" + query.getSearch() + "%";
            wrapper.and(w -> w
                    .like(User::getUsername, search)
                    .or().like(User::getEmail, search)
                    .or().like(User::getName, search));
        }

        // 角色过滤
        if (StringUtils.hasText(query.getRole())) {
            wrapper.eq(User::getRole, query.getRole());
        }

        // 启用状态过滤
        if (query.getIsActive() != null) {
            wrapper.eq(User::getIsActive, query.getIsActive());
        }

        // 封禁状态过滤
        if (query.getIsBanned() != null) {
            wrapper.eq(User::getIsBanned, query.getIsBanned());
        }

        // 排序
        boolean isAsc = "asc".equalsIgnoreCase(query.getSortOrder());
        String sortBy = StringUtils.hasText(query.getSortBy()) ? query.getSortBy() : "joinedAt";
        switch (sortBy) {
            case "username" -> wrapper.orderBy(true, isAsc, User::getUsername);
            case "email" -> wrapper.orderBy(true, isAsc, User::getEmail);
            case "lastLoginAt" -> wrapper.orderBy(true, isAsc, User::getLastLoginAt);
            default -> wrapper.orderBy(true, isAsc, User::getJoinedAt);
        }

        Page<User> userPage = new Page<>(pageRequest.page(), pageRequest.pageSize());
        Page<User> result = userMapper.selectPage(userPage, wrapper);

        // List path: entity->VO mapping only — NO stats / permissions enrichment
        // (intentional; protects the paginated read from N+1 explosions).
        // Detail path (getUserById) owns enrichment.
        List<AdminUserVO> voList = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());

        return PageResult.of(voList, result.getTotal(), pageRequest);
    }

    // ------------------------------------------------------------------
    // Single-item detail read (entity->VO + stats + permissions enrichment)
    // ------------------------------------------------------------------

    @Override
    public AdminUserVO getUserById(String id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        AdminUserVO vo = toVO(user);
        populateStats(vo, user.getId());
        populatePermissions(vo, user.getId(), user.getRole());
        return vo;
    }

    // ------------------------------------------------------------------
    // Projection helpers (entity -> AdminUserVO)
    // ------------------------------------------------------------------

    /**
     * 将 User 实体转为基础 AdminUserVO（不含 stats / permissions）。
     * 列表路径与详情路径的共用底座；enrichment 由详情路径单独追加。
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

        return vo;
    }

    private void populateStats(AdminUserVO vo, String userId) {
        AdminUserVO.UserStatsInfo stats = new AdminUserVO.UserStatsInfo();
        stats.setTotalSubmissions((int) userStatsReadPort.countSubmissionsByUserId(userId));
        stats.setAcceptedSubmissions((int) userStatsReadPort.countAcceptedProblemsByUserId(userId));
        stats.setTotalSolutions((int) userStatsReadPort.countSolutionsByUserId(userId));
        stats.setStreak(userStatsReadPort.calculateSubmissionStreak(userId));
        vo.setStats(stats);
    }

    private void populatePermissions(AdminUserVO vo, String userId, String role) {
        List<AdminUserVO.PermissionInfo> permissions = new ArrayList<>();
        if (StringUtils.hasText(role)) {
            populateRolePermissions(permissions, role);
        }
        populateDirectPermissions(permissions, userId);
        vo.setPermissions(permissions);
    }

    /**
     * 处理 role 权限。role 权限不带过期时间。
     * 通过 Dubbo RPC 查询 backend-auth 的 RoleTemplateService。
     */
    private void populateRolePermissions(List<AdminUserVO.PermissionInfo> sink, String role) {
        if (roleTemplateService == null) {
            return;
        }
        RpcResult<List<PermissionEntry>> result = roleTemplateService.getRoleTemplate(role);
        if (result == null || !result.success() || result.data() == null) {
            return;
        }
        for (PermissionEntry entry : result.data()) {
            AdminUserVO.PermissionInfo info = new AdminUserVO.PermissionInfo();
            info.setAction(entry.action());
            info.setResource(entry.resource());
            info.setSource("role");
            info.setExpiresAt(null);
            sink.add(info);
        }
    }

    /**
     * 处理 user 直接权限。过滤已过期项，避免 UI 显示无效授权。
     * 通过 AuthCutoverService 获取 authorization snapshot 的 permissionEntries。
     */
    private void populateDirectPermissions(List<AdminUserVO.PermissionInfo> sink, String userId) {
        if (authCutoverService == null) {
            return;
        }
        com.ulticode.auth.api.dto.AuthorizationSnapshotDTO snapshot;
        try {
            snapshot = authCutoverService.getSnapshot(userId);
        } catch (Exception e) {
            log.warn("Failed to fetch authorization snapshot for user {}: {}", userId, e.getMessage());
            return;
        }
        if (snapshot == null || snapshot.permissionEntries() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (PermissionEntry entry : snapshot.permissionEntries()) {
            if (!"direct".equals(entry.source())) {
                continue;
            }
            OffsetDateTime expiresAt = entry.expiresAt();
            if (expiresAt != null && !expiresAt.toLocalDateTime().isAfter(now)) {
                continue;
            }
            AdminUserVO.PermissionInfo info = new AdminUserVO.PermissionInfo();
            info.setAction(entry.action());
            info.setResource(entry.resource());
            info.setSource("direct");
            info.setExpiresAt(expiresAt != null ? expiresAt.toLocalDateTime() : null);
            sink.add(info);
        }
    }
}
