package com.ulticode.modules.admin.projection;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ulticode.modules.admin.dto.AuditLogQueryDTO;
import com.ulticode.modules.admin.entity.AuditLog;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Typed, normalized read criteria shared by every Admin audit read path.
 */
public final class AuditLogQuery {

    private final String performerId;
    private final String userId;
    private final String entityType;
    private final String entityId;
    private final String search;
    private final String action;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final int page;
    private final int limit;

    private AuditLogQuery(
            String performerId,
            String userId,
            String entityType,
            String entityId,
            String search,
            String action,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int page,
            int limit) {
        this.performerId = performerId;
        this.userId = userId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.search = search;
        this.action = action;
        this.startDate = startDate;
        this.endDate = endDate;
        this.page = page;
        this.limit = limit;
    }

    public static AuditLogQuery from(AuditLogQueryDTO query) {
        Objects.requireNonNull(query, "query");
        return new AuditLogQuery(
                query.getPerformerId(),
                query.getUserId(),
                query.getEntityType(),
                query.getEntityId(),
                normalizeSearch(query.getSearch()),
                query.getAction(),
                query.getStartDate(),
                query.getEndDate(),
                Objects.requireNonNull(query.getPage(), "query.page"),
                Objects.requireNonNull(query.getLimit(), "query.limit"));
    }

    /** Apply the same filters used by the aggregate SQL reads. */
    public LambdaQueryWrapper<AuditLog> toWrapper() {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (performerId != null) {
            wrapper.eq(AuditLog::getPerformerId, performerId);
        }
        if (userId != null) {
            wrapper.eq(AuditLog::getUserId, userId);
        }
        if (entityType != null) {
            wrapper.eq(AuditLog::getEntityType, entityType);
        }
        if (entityId != null) {
            wrapper.eq(AuditLog::getEntityId, entityId);
        }
        if (action != null) {
            wrapper.eq(AuditLog::getAction, action);
        }
        if (startDate != null) {
            wrapper.ge(AuditLog::getCreatedAt, startDate);
        }
        if (endDate != null) {
            wrapper.lt(AuditLog::getCreatedAt, endDate);
        }
        if (hasSearch()) {
            wrapper.and(filters -> filters
                    .like(AuditLog::getAction, search)
                    .or().like(AuditLog::getEntityType, search)
                    .or().like(AuditLog::getEntityId, search));
        }
        return wrapper;
    }

    private static String normalizeSearch(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public boolean hasSearch() {
        return search != null;
    }

    public String getPerformerId() {
        return performerId;
    }

    public String getUserId() {
        return userId;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getSearch() {
        return search;
    }

    public String getAction() {
        return action;
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }

    public LocalDateTime getEndDate() {
        return endDate;
    }

    public int getPage() {
        return page;
    }

    public int getLimit() {
        return limit;
    }
}
