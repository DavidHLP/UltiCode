package com.ulticode.modules.subscription.service;

import com.ulticode.modules.subscription.dto.CreateSubscriptionDTO;
import com.ulticode.modules.subscription.dto.SubscriptionCheckResultDTO;
import com.ulticode.modules.subscription.dto.SubscriptionDTO;
import com.ulticode.modules.subscription.entity.Subscription;

import java.util.Optional;

/**
 * Service interface for subscription operations.
 */
public interface SubscriptionService {

    /**
     * Check if a user has active premium access.
     * Admins and super admins always have premium access.
     *
     * @param userId  the user ID
     * @param userRole the user's role (optional)
     * @return the check result with access status and subscription details
     */
    SubscriptionCheckResultDTO hasPremiumAccess(String userId, String userRole);

    /**
     * Check if the current authenticated user has premium access.
     * Admins and super admins always have premium access.
     *
     * @return true if the user has premium access
     */
    boolean hasPremiumAccess();

    /**
     * Get the active subscription for a user.
     *
     * @param userId the user ID
     * @return the active subscription or empty
     */
    Optional<Subscription> getActiveSubscription(String userId);

    /**
     * Get the current user's subscription status.
     *
     * @return the subscription DTO or null if no active subscription
     */
    SubscriptionDTO getCurrentUserSubscription();

    /**
     * Create a new subscription.
     *
     * @param dto    the subscription data
     * @param userId the user ID
     * @return the created subscription
     */
    SubscriptionDTO createSubscription(CreateSubscriptionDTO dto, String userId);

    /**
     * Update subscription status.
     *
     * @param id     the subscription ID
     * @param status the new status
     * @param userId the user ID (for validation)
     * @return the updated subscription
     */
    SubscriptionDTO updateSubscriptionStatus(String id, String status, String userId);

    /**
     * Cancel a subscription.
     *
     * @param id     the subscription ID
     * @param userId the user ID (for validation)
     * @return the cancelled subscription
     */
    SubscriptionDTO cancelSubscription(String id, String userId);

    /**
     * Find subscription by ID.
     *
     * @param id the subscription ID
     * @return the subscription or empty
     */
    Optional<Subscription> findById(String id);

    /**
     * Convert entity to DTO.
     *
     * @param subscription the entity
     * @return the DTO
     */
    SubscriptionDTO toDTO(Subscription subscription);
}
