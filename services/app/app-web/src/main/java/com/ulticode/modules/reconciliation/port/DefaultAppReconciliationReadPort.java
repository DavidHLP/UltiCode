package com.ulticode.modules.reconciliation.port;

import com.ulticode.app.api.dto.ReconciliationOrphanCounts;
import com.ulticode.app.api.service.AppReconciliationReadPort;
import com.ulticode.auth.api.service.ReconciliationQueryService;
import com.ulticode.common.error.BaseErrorCode;
import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.rpc.RpcResult;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** App-side reconciliation adapter with Auth-owned parent existence checks. */
@Component
@Primary
@RequiredArgsConstructor
public class DefaultAppReconciliationReadPort implements AppReconciliationReadPort {

    private static final int RECONCILIATION_PAGE_SIZE = 500;

    @FunctionalInterface
    private interface ReferencePageQuery {
        List<UserReferenceCount> fetch(String afterId, int limit);
    }


    private final AppReconciliationReadMapper appReconciliationReadMapper;

    @DubboReference(group = "backend-auth", version = "1.0.0",
            timeout = 3000, retries = 2, check = false)
    private ReconciliationQueryService authQueryService;

    void setAuthQueryService(ReconciliationQueryService authQueryService) {
        this.authQueryService = authQueryService;
    }

    @Override
    public long countUserProfiles() {
        return appReconciliationReadMapper.countUserProfiles();
    }

    @Override
    public ReconciliationOrphanCounts countOrphans() {
        Set<String> tables = new HashSet<>(safe(appReconciliationReadMapper.existingChildTables()));
        long submissions = countOrphans(tables, "submissions",
                appReconciliationReadMapper::submissionUserCounts);
        long solutions = countOrphans(tables, "solutions",
                appReconciliationReadMapper::solutionUserCounts);
        long forumPosts = countOrphans(tables, "forum_posts",
                appReconciliationReadMapper::forumPostUserCounts);
        long notifications = countOrphans(tables, "notifications",
                appReconciliationReadMapper::notificationUserCounts);
        long profiles = countOrphans(tables, "user_profiles",
                appReconciliationReadMapper::userProfileAccountCounts);
        long participants = countOrphans(tables, "contest_participants",
                appReconciliationReadMapper::contestParticipantUserCounts);
        // Legacy achievement/follow tables are not part of the current App owner schema.
        return new ReconciliationOrphanCounts(
                submissions, solutions, forumPosts, notifications, profiles,
                participants, 0L, 0L, 0L);
    }

    private long countOrphans(Set<String> tables, String table, ReferencePageQuery query) {
        if (!tables.contains(table)) {
            throw unavailable();
        }
        String afterId = "";
        long orphanRows = 0L;
        while (true) {
            List<UserReferenceCount> page = safe(query.fetch(afterId, RECONCILIATION_PAGE_SIZE));
            if (page.isEmpty()) {
                return orphanRows;
            }
            Set<String> ids = new HashSet<>();
            for (UserReferenceCount reference : page) {
                if (reference == null || !hasText(reference.getAccountId())
                        || reference.getRowCount() < 0
                        || !ids.add(reference.getAccountId())) {
                    throw unavailable();
                }
            }
            Set<String> existing = existingIds(ids);
            for (UserReferenceCount reference : page) {
                if (!existing.contains(reference.getAccountId())) {
                    orphanRows += reference.getRowCount();
                }
            }
            String nextId = page.get(page.size() - 1).getAccountId();
            if (nextId.compareTo(afterId) <= 0) {
                throw unavailable();
            }
            afterId = nextId;
            if (page.size() < RECONCILIATION_PAGE_SIZE) {
                return orphanRows;
            }
        }
    }

    private Set<String> existingIds(Set<String> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        if (authQueryService == null) {
            throw unavailable();
        }
        RpcResult<Set<String>> result = authQueryService.existingUserIds(candidates);
        if (result == null || !result.success() || result.data() == null) {
            throw unavailable();
        }
        return result.data();
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private BusinessException unavailable() {
        return new BusinessException(BaseErrorCode.UNKNOWN_ERROR, "Auth reconciliation owner unavailable");
    }
}
