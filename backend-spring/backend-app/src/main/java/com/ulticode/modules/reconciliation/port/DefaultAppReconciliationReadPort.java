package com.ulticode.modules.reconciliation.port;

import com.ulticode.app.api.dto.ReconciliationOrphanCounts;
import com.ulticode.app.api.service.AppReconciliationReadPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * App-side adapter for {@link AppReconciliationReadPort}.
 *
 * <p>P7-RECON-CONTRACTS-001: owner-side replacement for the legacy
 * cross-owner JdbcTemplate orphan SQL (ADR-P7-OWNER-BOUNDARY-
 * RECONCILIATION-20260802 Decision 4). Counts come from App-owned
 * tables only; the users join is a permitted Q-read.
 */
@Component
@RequiredArgsConstructor
public class DefaultAppReconciliationReadPort implements AppReconciliationReadPort {

    private final AppReconciliationReadMapper appReconciliationReadMapper;

    @Override
    public long countUserProfiles() {
        return appReconciliationReadMapper.countUserProfiles();
    }

    @Override
    public ReconciliationOrphanCounts countOrphans() {
        return new ReconciliationOrphanCounts(
                appReconciliationReadMapper.countOrphanSubmissions(),
                appReconciliationReadMapper.countOrphanSolutions(),
                appReconciliationReadMapper.countOrphanForumPosts(),
                appReconciliationReadMapper.countOrphanNotifications(),
                appReconciliationReadMapper.countOrphanUserProfiles(),
                appReconciliationReadMapper.countOrphanContestParticipants(),
                appReconciliationReadMapper.countOrphanUserAchievements(),
                appReconciliationReadMapper.countOrphanUserFollowsByFollower(),
                appReconciliationReadMapper.countOrphanUserFollowsByFollowing());
    }
}
