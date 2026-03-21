package com.ulticode.modules.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AuditLogQueryDTO {
    private String performerId;
    private String userId;
    private String entityType;
    private String entityId;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Integer page = 1;
    private Integer limit = 50;
}