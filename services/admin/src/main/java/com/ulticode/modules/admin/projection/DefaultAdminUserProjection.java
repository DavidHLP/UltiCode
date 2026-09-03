package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.admin.error.AdminErrorCode;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.query.AdminUserDetailQuery;
import com.ulticode.modules.admin.query.AdminUserDetailResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adapter for {@link AdminUserProjection} using decoupled Auth and App RPC/port seams.
 */
@Service
public class DefaultAdminUserProjection implements AdminUserProjection {

    private final AdminUserEnricher userEnricher;
    private final AdminUserDetailQuery userDetailQuery;

    /**
     * Production constructor: account/profile list enrichment and the detail
     * use case are separate seams; this compatibility projection only
     * delegates the detail operation.
     */
    @Autowired
    public DefaultAdminUserProjection(
            AdminUserEnricher userEnricher,
            AdminUserDetailQuery userDetailQuery) {
        this.userEnricher = userEnricher;
        this.userDetailQuery = userDetailQuery;
    }

    /**
     * Test constructor retained for callers that inject the owner RPC mocks
     * directly. Role templates are intentionally ignored: the Auth
     * authorization snapshot already carries role and direct entries.
     */
    public DefaultAdminUserProjection(
            com.ulticode.auth.api.service.AccountQueryService accountQueryService,
            com.ulticode.app.api.service.UserProfileQueryService userProfileQueryService,
            com.ulticode.modules.admin.port.AdminSubmissionUserDetailStatsReadPort submissionStatsReadPort,
            com.ulticode.app.api.service.SolutionReadPort solutionReadPort,
            com.ulticode.auth.api.service.AuthorizationSnapshotService authorizationSnapshotService) {
        this.userEnricher = new AdminUserEnricher(
                null, userProfileQueryService, accountQueryService);
        this.userDetailQuery = new com.ulticode.modules.admin.query.DefaultAdminUserDetailQuery(
                userEnricher,
                submissionStatsReadPort,
                solutionReadPort,
                authorizationSnapshotService);
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

        AdminUserEnricher.AccountPage ownerPage =
                userEnricher.queryAccountsWithProfiles(accountQuery);
        List<AuthAccountDTO> accountList = ownerPage.accounts();
        long total = ownerPage.total();

        if (accountList == null || accountList.isEmpty()) {
            // Business-empty (Auth answered successfully with zero rows).
            return PageResult.of(Collections.emptyList(), total, pageRequest, DegradationStatus.OK);
        }

        List<AdminUserVO> voList = accountList.stream()
                .map(acc -> toVO(acc, ownerPage.profiles().get(acc.accountId())))
                .collect(Collectors.toList());
        return PageResult.of(voList, total, pageRequest, ownerPage.status());
    }

    @Override
    public AdminUserVO getUserById(String id) {
        AdminUserDetailResult result = userDetailQuery.loadUserDetail(id);
        if (result == null || result.failure() == AdminUserDetailResult.Failure.NOT_FOUND) {
            throw new BusinessException(AdminErrorCode.USER_NOT_FOUND);
        }
        if (result.failure() == AdminUserDetailResult.Failure.TRANSPORT_UNAVAILABLE) {
            throw new BusinessException(
                    AdminErrorCode.OWNER_QUERY_UNAVAILABLE,
                    "Admin user detail query unavailable");
        }
        return result.user();
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

}
