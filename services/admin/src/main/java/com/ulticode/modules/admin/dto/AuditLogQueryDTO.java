package com.ulticode.modules.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditLogQueryDTO {
    @Size(max = 40, message = "Performer ID must not exceed 40 characters")
    private String performerId;

    @Size(max = 40, message = "User ID must not exceed 40 characters")
    private String userId;

    @Size(max = 64, message = "Entity type must not exceed 64 characters")
    private String entityType;

    @Size(max = 64, message = "Entity ID must not exceed 64 characters")
    private String entityId;

    @Size(max = 200, message = "Search query must not exceed 200 characters")
    private String search;

    @Size(max = 64, message = "Action must not exceed 64 characters")
    private String action;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    @NotNull(message = "Page is required")
    @Min(value = 1, message = "Page must be at least 1")
    private Integer page = 1;

    @NotNull(message = "Limit is required")
    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 1000, message = "Limit must not exceed 1000")
    private Integer limit = 50;
}