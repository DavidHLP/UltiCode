package com.ulticode.modules.subscription.service;

import com.ulticode.modules.subscription.dto.CreateSubscriptionDTO;
import com.ulticode.modules.subscription.dto.SubscriptionCheckResultDTO;
import com.ulticode.modules.subscription.dto.SubscriptionDTO;

/**
 * Service interface for subscription operations.
 *
 * <p>Read paths return {@link SubscriptionDTO} directly so the entity never
 * escapes the service boundary. Read-side premium/expiry verdicts live in
 * {@link com.ulticode.modules.subscription.PremiumAccessPolicy}; entity&rarr;DTO
 * shaping is owned by the service implementation.
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
     * Get a user's active subscription projected to its DTO.
     *
     * <p>The load path applies the lazy ACTIVE&rarr;EXPIRED transition before
     * projecting, so callers always observe a status consistent with the
     * configured expiry.
     *
     * @param userId the user ID
     * @return the active subscription DTO, or {@code null} if there is none
     */
    SubscriptionDTO getActiveSubscription(String userId);

    /**
     * Get the current user's subscription status.
     *
     * @return the subscription DTO or {@code null} if no active subscription
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
     * Find a subscription by id and project it to its DTO.
     *
     * @param id the subscription ID
     * @return the subscription DTO, or {@code null} if no such subscription exists
     */
    SubscriptionDTO getSubscriptionById(String id);
}
