package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.auth.api.dto.AccountQueryDTO;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.common.response.DegradationStatus;
import com.ulticode.common.response.PageResult;
import com.ulticode.common.response.PaginationRequest;
import com.ulticode.modules.admin.dto.AdminUserQueryDTO;
import com.ulticode.modules.admin.dto.AdminUserVO;
import com.ulticode.modules.admin.port.adapter.AdminQueryDeadline;
import com.ulticode.modules.admin.port.adapter.CancellableQueryExecutor;
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

    /**
     * Production constructor for the lean list projection.
     */
    @Autowired
    public DefaultAdminUserProjection(AdminUserEnricher userEnricher) {
        this.userEnricher = userEnricher;
    }

    /**
     * Test constructor retained for callers that inject the owner RPC mocks
     * directly. Role templates are intentionally ignored: the Auth
     * authorization snapshot already carries role and direct entries.
     */
    public DefaultAdminUserProjection(
            com.ulticode.auth.api.service.AccountQueryService accountQueryService,
            com.ulticode.app.api.service.UserProfileQueryService userProfileQueryService) {
        this.userEnricher = new AdminUserEnricher(
                null,
                userProfileQueryService,
                accountQueryService,
                new CancellableQueryExecutor("admin-user-enrichment-test", 2),
                AdminQueryDeadline.system());
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
