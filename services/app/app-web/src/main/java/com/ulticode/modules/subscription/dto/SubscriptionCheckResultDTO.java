package com.ulticode.modules.subscription.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for subscription check result.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionCheckResultDTO {

    /**
     * Whether the user has premium access
     */
    private Boolean hasAccess;

    /**
     * Subscription details (null if no subscription)
     */
    private SubscriptionDetail subscription;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubscriptionDetail {
        private String plan;
        private String status;
        private LocalDateTime expiresAt;
    }
}
