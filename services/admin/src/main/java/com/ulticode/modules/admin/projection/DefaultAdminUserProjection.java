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
import com.ulticode.common.rpc.RpcPolicy;

/**
 * Adapter for {@link AdminUserProjection} using decoupled Auth and App RPC/port seams.
 */
@Slf4j
@Service
public class DefaultAdminUserProjection implements AdminUserProjection {

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AccountQueryService accountQueryService;

    @Autowired(required = false)
    @DubboReference(group = "backend-app", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private UserProfileQueryService userProfileQueryService;

    private final AdminUserStatsReadPort userStatsReadPort;
    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private AuthorizationSnapshotService authorizationSnapshotService;

    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = RpcPolicy.QUERY_TIMEOUT_MS, retries = RpcPolicy.QUERY_RETRIES, check = false)
    private RoleTemplateService roleTemplateService;

    /**
     * Production constructor: only the local port is constructor-injected;
     * the Dubbo references stay optional field injections so the admin
     * context loads while providers are down. Explicitly marked
     * {@link Autowired} because the test constructor below would otherwise
     * leave Spring without a unique candidate.
     */
    @Autowired
    public DefaultAdminUserProjection(AdminUserStatsReadPort userStatsReadPort) {
        this.userStatsReadPort = userStatsReadPort;
    }

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
            // Review 2026-08-25 FINAL P1: never disguise infrastructure failure
            // as an empty business result.
            throw new BusinessException(AdminErrorCode.UPSTREAM_UNAVAILABLE,
                    "AccountQueryService reference is not available");
        }

        RpcResult<AuthAccountDTO> rpcResult;
        try {
            rpcResult = accountQueryService.queryAccounts(accountQuery);
        } catch (Exception e) {
            log.error("AccountQueryService.queryAccounts unavailable: {}", e.getMessage());
            throw new BusinessException(AdminErrorCode.UPSTREAM_UNAVAILABLE, e);
        }
        if (rpcResult == null || !rpcResult.success() || rpcResult.page() == null) {
            String detail = rpcResult != null && rpcResult.error() != null ? rpcResult.error().message() : "empty response";
            log.error("AccountQueryService.queryAccounts failed: {}", detail);
            throw new BusinessException(AdminErrorCode.UPSTREAM_UNAVAILABLE, detail);
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
            RpcResult<List<UserProfileDTO>> profileRpcResult;
            try {
                profileRpcResult = userProfileQueryService.getProfilesByAccountIds(accountIds);
            } catch (Exception e) {
                // Partial degradation: rows keep Auth-owned fields; App-owned
                // display fields stay empty and the gap is logged explicitly.
                log.warn("UserProfileQueryService.getProfilesByAccountIds unavailable, returning partial rows: {}", e.getMessage());
                profileRpcResult = null;
            }
            if (profileRpcResult != null && profileRpcResult.success() && profileRpcResult.data() != null) {
                profileMap = profileRpcResult.data().stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toMap(UserProfileDTO::accountId, Function.identity(), (a, b) -> a));
            } else if (profileRpcResult != null) {
                log.warn("UserProfileQueryService.getProfilesByAccountIds failed, returning partial rows");
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
        AuthAccountDTO account = fetchAccountOrUnavailable(id);

        UserProfileDTO profile = null;
        if (userProfileQueryService != null) {
            try {
                RpcResult<UserProfileDTO> profileRpc = userProfileQueryService.getProfileByAccountId(id);
                if (profileRpc != null && profileRpc.success()) {
                    profile = profileRpc.data();
                } else {
                    log.warn("UserProfileQueryService.getProfileByAccountId failed for {}, returning partial detail", id);
                }
            } catch (Exception e) {
                log.warn("UserProfileQueryService.getProfileByAccountId unavailable for {}: {}", id, e.getMessage());
            }
        }

        AdminUserVO vo = toVO(account, profile);
        populateStats(vo, account.accountId());
        populatePermissions(vo, account.accountId(), account.role());
        return vo;
    }

    /**
     * Fetch one Auth-owned account with explicit failure semantics.
     *
     * <ul>
     *   <li>Reference missing, RPC throws, or response unusable &rarr; {@link
     *       AdminErrorCode#UPSTREAM_UNAVAILABLE} (503): infrastructure failure
     *       must never masquerade as "user not found".</li>
     *   <li>Provider answered but the account is absent &rarr; {@link
     *       AdminErrorCode#USER_NOT_FOUND}: a genuine business result.</li>
     * </ul>
     */
    private AuthAccountDTO fetchAccountOrUnavailable(String id) {
        if (accountQueryService == null) {
            throw new BusinessException(AdminErrorCode.UPSTREAM_UNAVAILABLE,
                    "AccountQueryService reference is not available");
        }
        RpcResult<AuthAccountDTO> rpcResult;
        try {
            rpcResult = accountQueryService.getAccountById(id);
        } catch (Exception e) {
            log.error("AccountQueryService.getAccountById unavailable for {}: {}", id, e.getMessage());
            throw new BusinessException(AdminErrorCode.UPSTREAM_UNAVAILABLE, e);
        }
        if (rpcResult == null || !rpcResult.success() || rpcResult.data() == null) {
            throw new BusinessException(AdminErrorCode.USER_NOT_FOUND);
        }
        return rpcResult.data();
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
