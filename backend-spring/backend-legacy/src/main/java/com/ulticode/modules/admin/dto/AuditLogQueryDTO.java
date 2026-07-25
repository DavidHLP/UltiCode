package com.ulticode.modules.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditLogQueryDTO {
    private String performerId;
    private String userId;
    private String entityType;
    private String entityId;

    @Size(max = 200, message = "Search query must not exceed 200 characters")
    private String search;

    private String action;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    @Min(value = 1, message = "Page must be at least 1")
    private Integer page = 1;

    @Min(value = 1, message = "Limit must be at least 1")
    @Max(value = 1000, message = "Limit must not exceed 1000")
    private Integer limit = 50;
}