package com.ulticode.modules.subscription.service.impl;

import com.ulticode.common.exception.BusinessException;
import com.ulticode.common.exception.ErrorCode;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.subscription.constants.SubscriptionPlan;
import com.ulticode.modules.subscription.constants.SubscriptionStatus;
import com.ulticode.modules.subscription.dto.CreateSubscriptionDTO;
import com.ulticode.modules.subscription.dto.SubscriptionCheckResultDTO;
import com.ulticode.modules.subscription.dto.SubscriptionDTO;
import com.ulticode.modules.subscription.entity.Subscription;
import com.ulticode.modules.subscription.mapper.SubscriptionMapper;
import com.ulticode.modules.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implementation of SubscriptionService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionMapper subscriptionMapper;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;

    @Override
    public SubscriptionCheckResultDTO hasPremiumAccess(String userId, String userRole) {
        // Admin and super admin users always have premium access
        if ("ADMIN".equals(userRole) || "SUPER_ADMIN".equals(userRole)) {
            return new SubscriptionCheckResultDTO(
                    true,
                    new SubscriptionCheckResultDTO.SubscriptionDetail(
                            "ADMIN",
                            SubscriptionStatus.ACTIVE.getValue(),
                            null
                    )
            );
        }

        // Find active subscription
        Subscription subscription = subscriptionMapper.findActiveByUserId(userId);

        if (subscription == null) {
            return new SubscriptionCheckResultDTO(false, null);
        }

        // Check if subscription has expired
        if (subscription.getExpiresAt() != null && subscription.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            // Update status to expired
            subscriptionMapper.updateStatus(subscription.getId(), SubscriptionStatus.EXPIRED.getValue());

            return new SubscriptionCheckResultDTO(
                    false,
                    new SubscriptionCheckResultDTO.SubscriptionDetail(
                            subscription.getPlan(),
                            SubscriptionStatus.EXPIRED.getValue(),
                            subscription.getExpiresAt()
                    )
            );
        }

        // Check if user has premium plan
        SubscriptionPlan plan = SubscriptionPlan.fromValue(subscription.getPlan());
        boolean hasAccess = plan != null && plan.isPremium();

        return new SubscriptionCheckResultDTO(
                hasAccess,
                new SubscriptionCheckResultDTO.SubscriptionDetail(
                        subscription.getPlan(),
                        subscription.getStatus(),
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

        // Check if user is admin
        if (currentUserProvider.hasRole("ADMIN") || currentUserProvider.hasRole("SUPER_ADMIN")) {
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
        return Optional.ofNullable(subscriptionMapper.findActiveByUserId(userId));
    }

    @Override
    public SubscriptionDTO getCurrentUserSubscription() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        Subscription subscription = subscriptionMapper.findActiveByUserId(userId);
        return toDTO(subscription);
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
        if (subscription == null) {
            return null;
        }

        SubscriptionDTO dto = new SubscriptionDTO();
        BeanUtils.copyProperties(subscription, dto);
        return dto;
    }
}
