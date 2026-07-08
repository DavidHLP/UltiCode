package com.ulticode.modules.subscription.controller;

import com.ulticode.common.annotation.RateLimit;
import com.ulticode.common.response.Result;
import com.ulticode.common.auth.CurrentUserProvider;
import com.ulticode.modules.subscription.dto.CreateSubscriptionDTO;
import com.ulticode.modules.subscription.dto.SubscriptionCheckResultDTO;
import com.ulticode.modules.subscription.dto.SubscriptionDTO;
import com.ulticode.modules.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for user subscription operations.
 */
@Tag(name = "User Subscription", description = "User subscription management endpoints")
@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class UserSubscriptionController {

    private final SubscriptionService subscriptionService;
    private final CurrentUserProvider currentUserProvider;

    /**
     * Get the current user's subscription status.
     *
     * @return the subscription status
     */
    @Operation(summary = "Get current user subscription", description = "Get the current authenticated user's subscription status")
    @GetMapping("/me")
    public Result<SubscriptionDTO> getCurrentUserSubscription() {
        SubscriptionDTO subscription = subscriptionService.getCurrentUserSubscription();
        return Result.success(subscription);
    }

    /**
     * Create a new subscription for the current user.
     *
     * @param dto the subscription data
     * @return the created subscription
     */
    @Operation(summary = "Create subscription", description = "Create a new subscription for the current user")
    @RateLimit(key = "subscription:create", limit = 20, period = 60)
    @PostMapping
    public Result<SubscriptionDTO> createSubscription(@Valid @RequestBody CreateSubscriptionDTO dto) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            return Result.error(40100, "Unauthorized");
        }
        SubscriptionDTO subscription = subscriptionService.createSubscription(dto, userId);
        return Result.success(subscription);
    }

    /**
     * Cancel a subscription.
     *
     * @param id the subscription ID
     * @return the cancelled subscription
     */
    @Operation(summary = "Cancel subscription", description = "Cancel an active subscription")
    @RateLimit(key = "subscription:cancel", limit = 20, period = 60)
    @PostMapping("/{id}/cancel")
    public Result<SubscriptionDTO> cancelSubscription(@PathVariable String id) {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            return Result.error(40100, "Unauthorized");
        }
        SubscriptionDTO subscription = subscriptionService.cancelSubscription(id, userId);
        return Result.success(subscription);
    }

    /**
     * Check if the current user has premium access.
     *
     * @return the premium access check result
     */
    @Operation(summary = "Check premium access", description = "Check if the current user has active premium access")
    @GetMapping("/check-premium")
    public Result<SubscriptionCheckResultDTO> checkPremiumAccess() {
        String userId = currentUserProvider.getCurrentUserId();
        if (userId == null) {
            return Result.error(40100, "Unauthorized");
        }

        // Get user role from security context
        String role = null;
        if (currentUserProvider.hasRole("ADMIN")) {
            role = "ADMIN";
        } else if (currentUserProvider.hasRole("SUPER_ADMIN")) {
            role = "SUPER_ADMIN";
        }

        SubscriptionCheckResultDTO result = subscriptionService.hasPremiumAccess(userId, role);
        return Result.success(result);
    }
}
