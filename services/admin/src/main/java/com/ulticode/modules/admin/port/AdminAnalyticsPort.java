package com.ulticode.modules.admin.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Port the admin analytics service uses to read cross-module aggregates.
 * Hides {@code ContestMapper}, {@code ContestParticipantMapper},
 * {@code SubscriptionMapper}, {@code SubmissionMapper}, {@code UserMapper},
 * and {@code ProblemMapper} from the admin module.
 *
 * <p>Replaces the worst single offender in candidate 1 of
 * {@code /tmp/architecture-review-1783485814.html}: the
 * {@code AdminAnalyticsServiceImpl} used to import five cross-module
 * mappers and compose their queries inline. The admin module now
 * imports only this port.
 *
 * <p><strong>Seam justification:</strong> the admin analytics surface has
 * three query slices (contest participation, revenue, and overview). The
 * port keeps owner fan-out behind those slices so reporters only consume
 * admin-owned read records.
 *
 * <p><b>Entity-leak closure:</b> the historic
 * {@link #loadContestData(LocalDateTime)} returned a record carrying
 * {@code List<Contest>} and {@link #listActiveSubscriptions()} returned
 * {@code List<Subscription>}, which forced
 * {@code AdminAnalyticsServiceImpl} to import the contest and
 * subscription entities. Both now return admin-owned projection records
 * ({@link ContestSummary}, {@link SubscriptionSummary}) that carry only
 * the fields the analytics reporters actually use.
 *
 * @author ulticode
 */
public interface AdminAnalyticsPort {

    /**
     * Load all contests in the period and a single batch of their
     * participants (replaces the previous per-contest N+1 loop with one
     * query each).
     *
     * @param startDate inclusive lower bound on {@code contest.startTime}
     * @return loaded contest summaries + per-contest participant counts +
     *         set of unique participant ids
     */
    ContestParticipationData loadContestData(LocalDateTime startDate);

    /**
     * @return list of all currently-active subscriptions projected to the
     *         admin-owned {@link SubscriptionSummary} shape (plan only) —
     *         consumed by the revenue reporter for plan/MRR aggregation
     */
    List<SubscriptionSummary> listActiveSubscriptions();

    /**
     * Load the dashboard overview in one coarse-grained query seam. The
     * adapter owns the cross-Owner call fan-out and returns only the fields
     * needed by the HTTP response.
     */
    AnalyticsOverviewData loadOverviewData(LocalDateTime from, LocalDateTime to);

    /**
     * Loaded data wrapper for {@link #loadContestData}. Mirrors the
     * internal record that the service previously inlined. Holds
     * admin-owned {@link ContestSummary} projections rather than
     * {@code Contest} entities.
     */
    record ContestParticipationData(
            List<ContestSummary> contests,
            Map<String, Long> participantsByContest,
            Set<String> uniqueParticipants
    ) {}

    record AnalyticsOverviewData(
            long totalUsers,
            long activeUsers,
            long totalSubmissions,
            long acceptedSubmissions,
            long totalContests,
            long activeSubscriptions
    ) {}
}
