package com.ulticode.modules.admin.projection;

import com.ulticode.app.api.dto.UserProfileDTO;
import com.ulticode.app.api.service.UserProfileQueryService;
import com.ulticode.auth.api.dto.AuthAccountDTO;
import com.ulticode.auth.api.dto.UserIdentityDTO;
import com.ulticode.auth.api.service.AccountQueryService;
import com.ulticode.auth.api.service.IdentityQueryService;
import com.ulticode.common.rpc.RpcResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Shared helper that enriches admin projections with user display data via
 * Dubbo RPC instead of direct {@code UserMapper} DB access.
 *
 * <p>Merges identity fields (username, role) from {@code IdentityQueryService}
 * (Auth-owned) with profile fields (name, avatar) from
 * {@code UserProfileQueryService} (App-owned) into a unified
 * {@link AdminUserSummary} keyed by account ID.
 *
 * <p>For single-item lookups ({@link #enrichOne}), email is populated via
 * {@code AccountQueryService} since it is only needed in detail views.
 *
 * <p>Follows the same dual-source Dubbo pattern as
 * {@code DefaultAdminUserProjection}. All Dubbo references use
 * {@code check=false} and {@code required=false} so the admin context loads
 * even when providers are down; enrichment simply returns empty results.
 */
@Slf4j
@Component
public class AdminUserEnricher {

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private IdentityQueryService identityQueryService;

    @Autowired(required = false)
    @DubboReference(group = "backend-app", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private UserProfileQueryService userProfileQueryService;

    @Autowired(required = false)
    @DubboReference(group = "backend-auth", version = "1.0.0", timeout = 3000, retries = 2, check = false)
    private AccountQueryService accountQueryService;

    /**
     * Batch-enrich a set of account IDs.
     *
     * <p>Email is not populated in batch mode (list views don't display it).
     * Use {@link #enrichOne} when email is required.
     *
     * @param accountIds IDs to look up; null/empty returns an empty map
     * @return map keyed by accountId; unknown IDs are absent (not null values)
     */
    public Map<String, AdminUserSummary> enrich(Set<String> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, UserIdentityDTO> identityMap = batchIdentities(accountIds);
        Map<String, UserProfileDTO> profileMap = batchProfiles(accountIds);

        return accountIds.stream()
                .filter(id -> identityMap.containsKey(id) || profileMap.containsKey(id))
                .collect(Collectors.toMap(
                        Function.identity(),
                        id -> {
                            UserIdentityDTO ident = identityMap.get(id);
                            UserProfileDTO profile = profileMap.get(id);
                            return new AdminUserSummary(
                                    id,
                                    ident != null ? ident.username() : null,
                                    ident != null ? ident.role() : null,
                                    profile != null ? profile.name() : null,
                                    profile != null ? profile.avatar() : null,
                                    null
                            );
                        }
                ));
    }

    /**
     * Enrich a single account ID.
     *
     * <p>Uses {@code AccountQueryService} to populate email (available in
     * {@code AuthAccountDTO} but not in the minimal identity projection).
     *
     * @return summary, or null if the account is unknown
     */
    public AdminUserSummary enrichOne(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return null;
        }

        // Try AccountQueryService first (has email + full account data)
        if (accountQueryService != null) {
            try {
                RpcResult<AuthAccountDTO> rpc = accountQueryService.getAccountById(accountId);
                if (rpc != null && rpc.success() && rpc.data() != null) {
                    AuthAccountDTO account = rpc.data();
                    // Profile fields from UserProfileQueryService
                    String name = null;
                    String avatar = null;
                    if (userProfileQueryService != null) {
                        try {
                            RpcResult<UserProfileDTO> profileRpc = userProfileQueryService.getProfileByAccountId(accountId);
                            if (profileRpc != null && profileRpc.success() && profileRpc.data() != null) {
                                name = profileRpc.data().name();
                                avatar = profileRpc.data().avatar();
                            }
                        } catch (Exception e) {
                            log.warn("UserProfileQueryService.getProfileByAccountId failed for {}: {}", accountId, e.getMessage());
                        }
                    }
                    return new AdminUserSummary(
                            accountId,
                            account.username(),
                            account.role(),
                            name,
                            avatar,
                            account.email()
                    );
                }
            } catch (Exception e) {
                log.warn("AccountQueryService.getAccountById failed for {}: {}", accountId, e.getMessage());
            }
        }

        // Fall back to batch enrichment (no email)
        Map<String, AdminUserSummary> result = enrich(Set.of(accountId));
        return result.get(accountId);
    }

    private Map<String, UserIdentityDTO> batchIdentities(Set<String> accountIds) {
        if (identityQueryService == null) {
            return Collections.emptyMap();
        }
        try {
            RpcResult<List<UserIdentityDTO>> rpc = identityQueryService.batchGetIdentity(accountIds);
            if (rpc == null || !rpc.success() || rpc.data() == null) {
                return Collections.emptyMap();
            }
            return rpc.data().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(UserIdentityDTO::accountId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("IdentityQueryService.batchGetIdentity failed for {} ids: {}", accountIds.size(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, UserProfileDTO> batchProfiles(Set<String> accountIds) {
        if (userProfileQueryService == null) {
            return Collections.emptyMap();
        }
        try {
            RpcResult<List<UserProfileDTO>> rpc = userProfileQueryService.getProfilesByAccountIds(accountIds);
            if (rpc == null || !rpc.success() || rpc.data() == null) {
                return Collections.emptyMap();
            }
            return rpc.data().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(UserProfileDTO::accountId, Function.identity(), (a, b) -> a));
        } catch (Exception e) {
            log.warn("UserProfileQueryService.getProfilesByAccountIds failed for {} ids: {}", accountIds.size(), e.getMessage());
            return Collections.emptyMap();
        }
    }
}
