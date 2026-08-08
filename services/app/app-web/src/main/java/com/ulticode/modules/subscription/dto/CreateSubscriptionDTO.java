package com.ulticode.modules.subscription.dto;

import com.ulticode.modules.subscription.constants.SubscriptionPlan;
import com.ulticode.modules.subscription.constants.SubscriptionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO for creating a new subscription.
 */
@Data
public class CreateSubscriptionDTO {

    @NotNull(message = "Plan is required")
    private String plan;

    /**
     * Optional status, defaults to ACTIVE
     */
    private String status;

    /**
     * Optional expiration date
     */
    private LocalDateTime expiresAt;

    /**
     * Optional transaction ID
     */
    private String transactionId;

    /**
     * Auto-renewal flag, defaults to true
     */
    private Boolean autoRenew;
}
