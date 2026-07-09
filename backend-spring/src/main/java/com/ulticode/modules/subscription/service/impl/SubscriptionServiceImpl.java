package com.ulticode.modules.subscription.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.subscription.PremiumAccessPolicy;
import com.ulticode.modules.subscription.constants.SubscriptionPlan;
import com.ulticode.modules.subscription.constants.SubscriptionStatus;
import com.ulticode.modules.subscription.dto.CreateSubscriptionDTO;
import com.ulticode.modules.subscription.dto.SubscriptionCheckResultDTO;
import com.ulticode.modules.subscription.dto.SubscriptionDTO;
import com.ulticode.modules.subscription.entity.Subscription;
import com.ulticode.modules.subscription.mapper.SubscriptionMapper;
import com.ulticode.modules.subscription.projection.SubscriptionReadProjection;
import com.ulticode.modules.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementation of {@link SubscriptionService}.
 *
 * <p>Read-side verdict logic now lives in {@link PremiumAccessPolicy}
 * (pure, no mutation) and read-side DTO shaping lives in
 * {@link SubscriptionReadProjection}. The service retains the write paths
 * (create / update status / cancel) and orchestrates the policy calls.
 *
 * <p><b>EXPIRED transition policy</b>: the {@code hasPremiumAccess} query
 * method used to call {@code subscriptionMapper.updateStatus(... EXPIRED ...)}
 * as a side effect inside a read. That was the architecture-review
 * correctness bug: a method named like a read silently mutating a row
 * raced with concurrent updates and broke read-after-write consistency.
 *
 * <p>The new layout:
 * <ul>
 *   <li>{@link #hasPremiumAccess(String, String)} &mdash; pure read; no DB
 *       write, ever. Returns the verdict via the policy.</li>
 *   <li>{@link #loadAndMarkExpired(Subscription)} &mdash; private helper
 *       called from the load-for-update path ({@link #getActiveSubscription}
 *       and {@link #getCurrentUserSubscription}). It is the only place the
 *       ACTIVE&rarr;EXPIRED transition runs. This is safe because both
 *       call sites already operate on rows the service is about to return
 *       to a caller, and the existing controller paths will pick up the
 *       fresh status on the next read.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionMapper subscriptionMapper;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;
    private final PremiumAccessPolicy premiumAccessPolicy;
    private final SubscriptionReadProjection subscriptionReadProjection;

    @Override
    public SubscriptionCheckResultDTO hasPremiumAccess(String userId, String userRole) {
        if (premiumAccessPolicy.isAdminBypass(userRole)) {
            return new SubscriptionCheckResultDTO(
                    true,
                    new SubscriptionCheckResultDTO.SubscriptionDetail(
                            PremiumAccessPolicy.ADMIN_ROLE,
                            SubscriptionStatus.ACTIVE.getValue(),
                            null
                    )
            );
        }

        Subscription subscription = subscriptionMapper.findActiveByUserId(userId);
        if (subscription == null) {
            return new SubscriptionCheckResultDTO(false, null);
        }

        boolean hasAccess = premiumAccessPolicy.hasActivePremium(subscription);
        String detailStatus = premiumAccessPolicy.hasExpired(subscription)
                ? SubscriptionStatus.EXPIRED.getValue()
                : subscription.getStatus();
        return new SubscriptionCheckResultDTO(
                hasAccess,
                new SubscriptionCheckResultDTO.SubscriptionDetail(
                        subscription.getPlan(),
                        detailStatus,
                        subscription.getExpiresAt()
                )
        );
    }

    @Override
    public boolean hasPremiumAccess() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            return false;
        }
        if (currentUserProvider.hasRole(PremiumAccessPolicy.ADMIN_ROLE)
                || currentUserProvider.hasRole(PremiumAccessPolicy.SUPER_ADMIN_ROLE)) {
            return true;
        }
        SubscriptionCheckResultDTO result = hasPremiumAccess(userId, null);
        return Boolean.TRUE.equals(result.getHasAccess());
    }

    @Override
    public Optional<Subscription> getActiveSubscription(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        Subscription subscription = subscriptionMapper.findActiveByUserId(userId);
        if (subscription == null) {
            return Optional.empty();
        }
        return Optional.of(loadAndMarkExpired(subscription));
    }

    @Override
    public SubscriptionDTO getCurrentUserSubscription() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Subscription subscription = subscriptionMapper.findActiveByUserId(userId);
        if (subscription != null) {
            subscription = loadAndMarkExpired(subscription);
        }
        return subscriptionReadProjection.toDTO(subscription);
    }

    @Override
    @Transactional
    public SubscriptionDTO createSubscription(CreateSubscriptionDTO dto, String userId) {
        // Validate plan
        SubscriptionPlan plan = SubscriptionPlan.fromValue(dto.getPlan());
        if (plan == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid subscription plan");
        }

        // Check if user already has an active subscription
        Subscription existingSubscription = subscriptionMapper.findActiveByUserId(userId);
        if (existingSubscription != null) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_ALREADY_ACTIVE,
                    "User already has an active subscription");
        }

        Subscription subscription = new Subscription();
        subscription.setUserId(userId);
        subscription.setPlan(dto.getPlan());
        subscription.setStatus(dto.getStatus() != null ? dto.getStatus() : SubscriptionStatus.ACTIVE.getValue());
        subscription.setExpiresAt(dto.getExpiresAt());
        subscription.setTransactionId(dto.getTransactionId());
        subscription.setAutoRenew(dto.getAutoRenew() != null ? dto.getAutoRenew() : true);
        subscription.setIsDeleted(false);

        subscriptionMapper.insert(subscription);

        log.info("Subscription created: {} for user {}", subscription.getId(), userId);

        return toDTO(subscription);
    }

    @Override
    @Transactional
    public SubscriptionDTO updateSubscriptionStatus(String id, String status, String userId) {
        Subscription subscription = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        // Validate ownership
        if (!subscription.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot modify another user's subscription");
        }

        // Validate status
        SubscriptionStatus newStatus = SubscriptionStatus.fromValue(status);
        if (newStatus == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid subscription status");
        }

        subscription.setStatus(status);
        subscriptionMapper.updateById(subscription);

        log.info("Subscription status updated: {} to {}", id, status);

        return toDTO(subscription);
    }

    @Override
    @Transactional
    public SubscriptionDTO cancelSubscription(String id, String userId) {
        Subscription subscription = findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND));

        // Validate ownership
        if (!subscription.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "Cannot cancel another user's subscription");
        }

        // Check if already cancelled
        if (SubscriptionStatus.CANCELLED.getValue().equals(subscription.getStatus())) {
            throw new BusinessException(ErrorCode.SUBSCRIPTION_EXPIRED,
                    "Subscription is already cancelled");
        }

        subscriptionMapper.cancelById(id);

        // Fetch updated subscription
        subscription = findById(id).orElseThrow();
        subscription.setCancelledAt(LocalDateTime.now(clock));

        log.info("Subscription cancelled: {}", id);

        return toDTO(subscription);
    }

    @Override
    public Optional<Subscription> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(subscriptionMapper.selectById(id));
    }

    @Override
    public SubscriptionDTO toDTO(Subscription subscription) {
        return subscriptionReadProjection.toDTO(subscription);
    }

    /**
     * Lazy ACTIVE&rarr;EXPIRED transition on the write path.
     *
     * <p>If the loaded row is still flagged {@code ACTIVE} but has crossed
     * its {@code expiresAt} (as evaluated by
     * {@link PremiumAccessPolicy#hasExpired(Subscription)}), persist
     * {@code EXPIRED} on the row and return the in-memory updated entity.
     * Otherwise return the row unchanged.
     *
     * <p>This is the only place the transition runs &mdash; it is never
     * called from the read-only {@link #hasPremiumAccess(String, String)}
     * query path. Both call sites ({@link #getActiveSubscription(String)}
     * and {@link #getCurrentUserSubscription()}) are load-for-view paths,
     * so persisting the correct status keeps the row consistent with the
     * verdict the policy will produce on the next read.
     */
    private Subscription loadAndMarkExpired(Subscription subscription) {
        if (premiumAccessPolicy.hasExpired(subscription)
                && SubscriptionStatus.ACTIVE.getValue().equals(subscription.getStatus())) {
            subscriptionMapper.updateStatus(subscription.getId(), SubscriptionStatus.EXPIRED.getValue());
            subscription.setStatus(SubscriptionStatus.EXPIRED.getValue());
        }
        return subscription;
    }
}
