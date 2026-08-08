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
 * <p><strong>Seam justification — five call sites justify it:</strong>
 * the admin module's contest participation report, revenue report, and
 * overview all read across module boundaries; concentrating the reads
 * behind one port lets the providers ship their own adapters and the
 * admin module focus on shape.
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
     * @return total subscriber count for active subscriptions
     */
    long countActiveSubscriptions();

    /**
     * @return list of all currently-active subscriptions projected to the
     *         admin-owned {@link SubscriptionSummary} shape (plan only) —
     *         consumed by the revenue reporter for plan/MRR aggregation
     */
    List<SubscriptionSummary> listActiveSubscriptions();

    /**
     * @return distinct user count who submitted in the period
     */
    long countDistinctSubmittersInRange(LocalDateTime from, LocalDateTime to);

    /**
     * @return total submission count in the period
     */
    long countSubmissionsInRange(LocalDateTime from);

    /**
     * @return accepted submission count in the period
     */
    long countAcceptedSubmissionsInRange(LocalDateTime from);

    /**
     * @return total contest count in the period
     */
    long countContestsInRange(LocalDateTime from);

    /**
     * @return total user count (active or not)
     */
    long countAllUsers();

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
}