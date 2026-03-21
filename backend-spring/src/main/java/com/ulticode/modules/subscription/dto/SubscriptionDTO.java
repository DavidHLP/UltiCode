package com.ulticode.modules.subscription.dto;

import com.ulticode.modules.subscription.constants.SubscriptionPlan;
import com.ulticode.modules.subscription.constants.SubscriptionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO for subscription data.
 */
@Data
public class SubscriptionDTO {

    private String id;

    private String userId;

    private String plan;

    private String status;

    private LocalDateTime expiresAt;

    private LocalDateTime cancelledAt;

    private String transactionId;

    private Boolean autoRenew;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
