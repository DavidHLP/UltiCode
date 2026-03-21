package com.ulticode.modules.subscription.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.subscription.dto.SubscriptionDTO;
import com.ulticode.modules.subscription.entity.Subscription;
import com.ulticode.modules.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for admin subscription operations.
 */
@Tag(name = "Admin Subscription", description = "Admin subscription management endpoints")
@RestController
@RequestMapping("/api/admin/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * Get subscription by ID.
     *
     * @param id the subscription ID
     * @return the subscription
     */
    @Operation(summary = "Get subscription by ID", description = "Get subscription details by ID (admin only)")
    @GetMapping("/{id}")
    public Result<SubscriptionDTO> getSubscriptionById(
            @Parameter(description = "Subscription ID")
            @PathVariable String id) {
        Subscription subscription = subscriptionService.findById(id)
                .orElse(null);
        return Result.success(subscriptionService.toDTO(subscription));
    }

    /**
     * Get user's active subscription.
     *
     * @param userId the user ID
     * @return the active subscription
     */
    @Operation(summary = "Get user subscription", description = "Get a user's active subscription (admin only)")
    @GetMapping("/user/{userId}")
    public Result<SubscriptionDTO> getUserSubscription(
            @Parameter(description = "User ID")
            @PathVariable String userId) {
        Subscription subscription = subscriptionService.getActiveSubscription(userId)
                .orElse(null);
        return Result.success(subscriptionService.toDTO(subscription));
    }
}
