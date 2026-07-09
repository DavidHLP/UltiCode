package com.ulticode.modules.subscription;

import com.ulticode.modules.subscription.constants.SubscriptionPlan;
import com.ulticode.modules.subscription.constants.SubscriptionStatus;
import com.ulticode.modules.subscription.entity.Subscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Pure access policy for premium subscriptions.
 *
 * <p>Owns the three rules that decide whether a caller has premium access,
 * with no side effects and no persistence concerns:
 * <ul>
 *   <li><b>Admin bypass</b> &mdash; {@link #isAdminBypass(String)} answers
 *       whether the caller's role short-circuits the policy.</li>
 *   <li><b>Expiry evaluation</b> &mdash; {@link #hasExpired(Subscription)}
 *       compares {@code expiresAt} against the injected {@link Clock} without
 *       mutating the row. The actual ACTIVE&rarr;EXPIRED transition lives in
 *       the write path (see {@code SubscriptionServiceImpl#loadAndMarkExpired}
 *       &mdash; invoked from {@code getActiveSubscription} and other load
 *       paths, never from a read-only query).</li>
 *   <li><b>Plan-tier predicate</b> &mdash; {@link #hasActivePremium(Subscription)}
 *       combines the {@code ACTIVE} status check, the expiry check, and the
 *       premium plan check into the single boolean the service needs.</li>
 * </ul>
 *
 * <p><b>Why pure:</b> keeping the policy stateless means {@code hasPremiumAccess}
 * &mdash; which the frontend polls on every navigation &mdash; can no longer
 * perform a database write as a side effect. That was the correctness bug
 * called out in the architecture review: a method named like a read was
 * silently transitioning rows to EXPIRED, racing with concurrent updates
 * and breaking read-after-write consistency.
 */
@Component
@RequiredArgsConstructor
public class PremiumAccessPolicy {

    /**
     * Role string that grants an unconditional admin bypass.
     */
    public static final String ADMIN_ROLE = "ADMIN";

    /**
     * Role string that grants an unconditional super-admin bypass.
     */
    public static final String SUPER_ADMIN_ROLE = "SUPER_ADMIN";

    private final Clock clock;

    /**
     * Whether the given role should bypass all subscription checks.
     *
     * <p>Centralises the role check that previously lived inline in
     * {@code SubscriptionServiceImpl} and {@code UserSubscriptionController}.
     * Treats {@code null} as "no role" so callers don't have to null-guard.
     *
     * @param role the user's role string ({@code null} safe)
     * @return {@code true} if the role is {@code ADMIN} or {@code SUPER_ADMIN}
     */
    public boolean isAdminBypass(String role) {
        return ADMIN_ROLE.equals(role) || SUPER_ADMIN_ROLE.equals(role);
    }

    /**
     * Whether the given subscription is past its expiry timestamp.
     *
     * <p>Pure: never mutates the row. The caller decides what to do with
     * the verdict. A subscription with no {@code expiresAt} is treated as
     * non-expiring.
     *
     * @param subscription the subscription row (may be {@code null})
     * @return {@code true} if a non-null subscription has a non-null
     *         {@code expiresAt} in the past relative to the injected clock
     */
    public boolean hasExpired(Subscription subscription) {
        if (subscription == null) {
            return false;
        }
        LocalDateTime expiresAt = subscription.getExpiresAt();
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now(clock));
    }

    /**
     * The single verdict {@code hasPremiumAccess} needs.
     *
     * <p>{@code true} if and only if all three hold:
     * <ol>
     *   <li>the row exists and is not {@code null},</li>
     *   <li>its {@code status} is {@code ACTIVE} and it has not expired,</li>
     *   <li>its {@code plan} is a premium plan
     *       ({@link SubscriptionPlan#isPremium()}).</li>
     * </ol>
     *
     * <p>Note: the row's persisted {@code status} may still read as
     * {@code ACTIVE} even when the row has crossed its expiry timestamp
     * &mdash; the lazy ACTIVE&rarr;EXPIRED transition in the write path
     * may not have run yet. This method therefore evaluates expiry off
     * {@code expiresAt}, not off the stale {@code status} column, and is
     * the single source of truth for the "is this user actually premium"
     * question.
     *
     * @param subscription the subscription row (may be {@code null})
     * @return {@code true} if the user has active premium access
     */
    public boolean hasActivePremium(Subscription subscription) {
        if (subscription == null) {
            return false;
        }
        if (!SubscriptionStatus.ACTIVE.getValue().equals(subscription.getStatus())) {
            return false;
        }
        if (hasExpired(subscription)) {
            return false;
        }
        SubscriptionPlan plan = SubscriptionPlan.fromValue(subscription.getPlan());
        return plan != null && plan.isPremium();
    }
}
