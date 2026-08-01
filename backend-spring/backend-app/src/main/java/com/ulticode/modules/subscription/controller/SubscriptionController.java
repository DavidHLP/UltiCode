package com.ulticode.modules.subscription.controller;

import com.ulticode.common.response.Result;
import com.ulticode.modules.subscription.dto.SubscriptionDTO;
import com.ulticode.modules.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for admin subscription operations.
 */
@Tag(name = "Admin Subscription", description = "Admin subscription management endpoints")
@RestController
@RequestMapping("/admin/subscriptions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
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
        return Result.success(subscriptionService.getSubscriptionById(id));
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
        return Result.success(subscriptionService.getActiveSubscription(userId));
    }
}
