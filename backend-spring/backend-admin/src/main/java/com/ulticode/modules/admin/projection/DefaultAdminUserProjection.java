package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.api.service.UserProfileQueryService;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.PermissionEntry;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.RoleTemplateService;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.common.rpc.RpcResult;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.port.AdminUserStatsReadPort;
import com.ulticode.auth.api.service.AuthorizationSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adapter for {@link AdminUserProjection} using decoupled Auth and App RPC/port seams.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAdminUserProjection implements AdminUserProjection {

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private AccountQueryService accountQueryService;

    @Autowired(required = false)
    @DubboReference(group = "backend-app", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private UserProfileQueryService userProfileQueryService;

    private final AdminUserStatsReadPort userStatsReadPort;
    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private AuthorizationSnapshotService authorizationSnapshotService;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private RoleTemplateService roleTemplateService;

    // Constructors for test injection when Spring/Dubbo context is unavailable
    public DefaultAdminUserProjection(AccountQueryService accountQueryService,
                                      UserProfileQueryService userProfileQueryService,
                                      AdminUserStatsReadPort userStatsReadPort,
                                      AuthorizationSnapshotService authorizationSnapshotService,
                                      RoleTemplateService roleTemplateService) {
        this.accountQueryService = accountQueryService;
        this.userProfileQueryService = userProfileQueryService;
        this.userStatsReadPort = userStatsReadPort;
        this.authorizationSnapshotService = authorizationSnapshotService;
        this.roleTemplateService = roleTemplateService;
    }

    @Override
    public PageResult<AdminUserVO> getUsers(AdminUserQueryDTO query) {
        PaginationRequest pageRequest = PaginationRequest.of(query.getPage(), query.getLimit(), 10);

        AccountQueryDTO accountQuery = new AccountQueryDTO(
                query.getSearch(),
                query.getRole(),
                query.getIsActive(),
                query.getIsBanned(),
                pageRequest.page(),
                pageRequest.pageSize(),
                query.getSortBy(),
                query.getSortOrder()
        );

        if (accountQueryService == null) {
            return PageResult.of(Collections.emptyList(), 0L, pageRequest);
        }

        RpcResult<AuthAccountDTO> rpcResult = accountQueryService.queryAccounts(accountQuery);
        if (rpcResult == null || !rpcResult.success() || rpcResult.page() == null) {
            return PageResult.of(Collections.emptyList(), 0L, pageRequest);
        }

        RpcResult.Page page = rpcResult.page();
        @SuppressWarnings("unchecked")
        List<AuthAccountDTO> accountList = (List<AuthAccountDTO>) page.items();
        long total = page.total() != null ? page.total() : 0L;

        if (accountList == null || accountList.isEmpty()) {
            return PageResult.of(Collections.emptyList(), total, pageRequest);
        }

        Set<String> accountIds = accountList.stream()
                .map(AuthAccountDTO::accountId)
                .collect(Collectors.toSet());

        Map<String, UserProfileDTO> profileMap = Collections.emptyMap();
        if (userProfileQueryService != null) {
            RpcResult<List<UserProfileDTO>> profileRpcResult = userProfileQueryService.getProfilesByAccountIds(accountIds);
            if (profileRpcResult != null && profileRpcResult.success() && profileRpcResult.data() != null) {
                profileMap = profileRpcResult.data().stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(UserProfileDTO::accountId, Function.identity(), (a, b) -> a));
            }
        }

        final Map<String, UserProfileDTO> finalProfileMap = profileMap;
        List<AdminUserVO> voList = accountList.stream()
                .map(acc -> toVO(acc, finalProfileMap.get(acc.accountId())))
                .collect(Collectors.toList());

        return PageResult.of(voList, total, pageRequest);
    }

    @Override
    public AdminUserVO getUserById(String id) {
        if (accountQueryService == null) {
            throw new BusinessException(AdminErrorCode.USER_NOT_FOUND);
        }

        RpcResult<AuthAccountDTO> rpcResult = accountQueryService.getAccountById(id);
        if (rpcResult == null || !rpcResult.success() || rpcResult.data() == null) {
            throw new BusinessException(AdminErrorCode.USER_NOT_FOUND);
        }

        AuthAccountDTO account = rpcResult.data();

        UserProfileDTO profile = null;
        if (userProfileQueryService != null) {
            RpcResult<UserProfileDTO> profileRpc = userProfileQueryService.getProfileByAccountId(id);
            if (profileRpc != null && profileRpc.success()) {
                profile = profileRpc.data();
            }
        }

        AdminUserVO vo = toVO(account, profile);
        populateStats(vo, account.accountId());
        populatePermissions(vo, account.accountId(), account.role());
        return vo;
    }

    private AdminUserVO toVO(AuthAccountDTO account, UserProfileDTO profile) {
        if (account == null) {
            return null;
        }

        AdminUserVO vo = new AdminUserVO();
        vo.setId(account.accountId());
        vo.setUsername(account.username());
        vo.setEmail(account.email());
        vo.setRole(account.role());
        vo.setIsActive(account.active());
        vo.setIsBanned(account.banned());
        vo.setBanReason(account.bannedReason());
        vo.setBannedUntil(account.bannedUntil());
        vo.setJoinedAt(account.joinedAt());
        vo.setLastLoginAt(account.lastLoginAt());

        if (profile != null) {
            vo.setName(profile.name());
            vo.setAvatar(profile.avatar());
        }

        return vo;
    }

    private void populateStats(AdminUserVO vo, String userId) {
        if (userStatsReadPort == null) {
            return;
        }
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

    private void populateDirectPermissions(List<AdminUserVO.PermissionInfo> sink, String userId) {
        if (authorizationSnapshotService == null) {
            return;
        }
        com.ulticode.auth.api.dto.AuthorizationSnapshotDTO snapshot;
        try {
            RpcResult<com.ulticode.auth.api.dto.AuthorizationSnapshotDTO> rpc =
                    authorizationSnapshotService.getSnapshot(userId);
            if (rpc == null || !rpc.success() || rpc.data() == null) {
                return;
            }
            snapshot = rpc.data();
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
